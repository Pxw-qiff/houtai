package com.chuamgwei.module.auth.service;

import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.auth.config.AuthProperties;
import com.chuamgwei.module.auth.entity.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

/**
 * 积分系统登录态签发与解析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_USER_UUID = "userUuid";
    private static final String CLAIM_ADMIN_PERMISSIONS = "adminPermissions";

    private final AuthProperties authProperties;
    private final UserMapper userMapper;

    /**
     * 根据用户信息生成积分系统访问令牌
     */
    public String createToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + authProperties.getJwtExpireMinutes() * 60L * 1000L);
        return Jwts.builder()
                .setSubject(user.getUserUuid())
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ADMIN_PERMISSIONS, user.getAdminPermissions())
                .setIssuedAt(now)
                .setExpiration(expiresAt)
                .signWith(SignatureAlgorithm.HS256, getJwtSecretBytes())
                .compact();
    }

    /**
     * 解析并校验服务端访问令牌，最终身份状态以共享 users 表为准
     */
    public AuthPrincipal parseToken(String token) {
        String tokenFingerprint = getTokenFingerprint(token);
        Claims claims = parseClaims(token, tokenFingerprint);

        String subject = normalizeBlank(claims.getSubject());
        Object claimUserUuidValue = claims.get(CLAIM_USER_UUID);
        String userUuid = subject;
        if (userUuid == null && claimUserUuidValue instanceof String) {
            userUuid = normalizeBlank((String) claimUserUuidValue);
        }
        if (userUuid == null) {
            log.warn("访问令牌校验失败: reason=missing_user_uuid, tokenFingerprint={}, subject={}, claimUserUuid={}",
                    tokenFingerprint, subject, claimUserUuidValue);
            throw new RuntimeException("访问令牌缺少用户UUID");
        }

        User user = userMapper.selectById(userUuid);
        if (user == null) {
            log.warn("访问令牌校验失败: reason=user_not_found, tokenFingerprint={}, userUuid={}",
                    tokenFingerprint, userUuid);
            throw new RuntimeException("用户不存在");
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() != 0) {
            log.warn("访问令牌校验失败: reason=user_deleted, tokenFingerprint={}, userUuid={}, isDeleted={}",
                    tokenFingerprint, userUuid, user.getIsDeleted());
            throw new RuntimeException("用户账号已删除");
        }
        if (user.getIsBanned() != null && user.getIsBanned() != 0) {
            log.warn("访问令牌校验失败: reason=user_banned, tokenFingerprint={}, userUuid={}, isBanned={}",
                    tokenFingerprint, userUuid, user.getIsBanned());
            throw new RuntimeException("用户账号已封禁");
        }

        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserUuid(user.getUserUuid());
        principal.setUsername(user.getUsername());
        principal.setAdminPermissions(user.getAdminPermissions());
        log.debug("访问令牌校验通过: tokenFingerprint={}, userUuid={}, username={}",
                tokenFingerprint, user.getUserUuid(), user.getUsername());
        return principal;
    }

    /**
     * 解析 JWT 并输出可定位的失败分类，不记录 token 原文和密钥
     */
    private Claims parseClaims(String token, String tokenFingerprint) {
        int tokenPartCount = countTokenParts(token);
        try {
            return Jwts.parser()
                    .setSigningKey(getJwtSecretBytes())
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            Claims expiredClaims = e.getClaims();
            log.warn("访问令牌校验失败: reason=expired, tokenFingerprint={}, tokenParts={}, expiredAt={}, now={}",
                    tokenFingerprint,
                    tokenPartCount,
                    expiredClaims == null ? null : expiredClaims.getExpiration(),
                    new Date());
            throw new RuntimeException("访问令牌已过期", e);
        } catch (SignatureException e) {
            log.warn("访问令牌校验失败: reason=signature_invalid, tokenFingerprint={}, tokenParts={}",
                    tokenFingerprint, tokenPartCount);
            throw new RuntimeException("访问令牌签名错误", e);
        } catch (MalformedJwtException e) {
            log.warn("访问令牌校验失败: reason=malformed, tokenFingerprint={}, tokenParts={}, message={}",
                    tokenFingerprint, tokenPartCount, e.getMessage());
            throw new RuntimeException("访问令牌格式错误", e);
        } catch (UnsupportedJwtException e) {
            log.warn("访问令牌校验失败: reason=unsupported, tokenFingerprint={}, tokenParts={}, message={}",
                    tokenFingerprint, tokenPartCount, e.getMessage());
            throw new RuntimeException("访问令牌格式不支持", e);
        } catch (IllegalArgumentException e) {
            log.warn("访问令牌校验失败: reason=blank_or_illegal, tokenFingerprint={}, tokenParts={}, message={}",
                    tokenFingerprint, tokenPartCount, e.getMessage());
            throw new RuntimeException("访问令牌为空或非法", e);
        } catch (JwtException e) {
            log.warn("访问令牌校验失败: reason=jwt_error, tokenFingerprint={}, tokenParts={}, message={}",
                    tokenFingerprint, tokenPartCount, e.getMessage());
            throw new RuntimeException("访问令牌解析失败", e);
        }
    }

    /**
     * 统计 JWT 分段数量，用于快速识别格式类问题
     */
    private int countTokenParts(String token) {
        String normalizedToken = normalizeBlank(token);
        if (normalizedToken == null) {
            return 0;
        }
        return normalizedToken.split("\\.", -1).length;
    }

    /**
     * 生成 token 指纹，用于日志关联，不暴露 token 原文
     */
    private String getTokenFingerprint(String token) {
        String normalizedToken = normalizeBlank(token);
        if (normalizedToken == null) {
            return "empty";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(normalizedToken.getBytes(StandardCharsets.UTF_8));
            return toHexPrefix(digest, 6);
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }

    /**
     * 转换指定字节数的十六进制前缀
     */
    private String toHexPrefix(byte[] bytes, int byteCount) {
        StringBuilder builder = new StringBuilder(byteCount * 2);
        for (int i = 0; i < bytes.length && i < byteCount; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    /**
     * 将空白字符串归一为空值
     */
    private String normalizeBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 使用原始字符串密钥字节，保持与 Node jsonwebtoken 的 HS256 签名行为一致
     */
    private byte[] getJwtSecretBytes() {
        return authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

}