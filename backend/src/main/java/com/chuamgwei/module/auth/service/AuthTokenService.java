package com.chuamgwei.module.auth.service;

import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.auth.config.AuthProperties;
import com.chuamgwei.module.auth.entity.AuthPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * 积分系统登录态签发与解析服务
 */
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
        Claims claims = Jwts.parser()
                .setSigningKey(getJwtSecretBytes())
                .parseClaimsJws(token)
                .getBody();

        String userUuid = claims.getSubject();
        if (userUuid == null || userUuid.trim().isEmpty()) {
            userUuid = claims.get(CLAIM_USER_UUID, String.class);
        }
        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new RuntimeException("访问令牌缺少用户UUID");
        }

        User user = userMapper.selectById(userUuid);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() != 0) {
            throw new RuntimeException("用户账号已删除");
        }
        if (user.getIsBanned() != null && user.getIsBanned() != 0) {
            throw new RuntimeException("用户账号已封禁");
        }

        AuthPrincipal principal = new AuthPrincipal();
        principal.setUserUuid(user.getUserUuid());
        principal.setUsername(user.getUsername());
        principal.setAdminPermissions(user.getAdminPermissions());
        return principal;
    }

    /**
     * 使用原始字符串密钥字节，保持与 Node jsonwebtoken 的 HS256 签名行为一致
     */
    private byte[] getJwtSecretBytes() {
        return authProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
    }

}