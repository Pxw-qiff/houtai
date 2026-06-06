package com.chuamgwei.config;

import com.chuamgwei.common.AuthException;
import com.chuamgwei.common.RequestContext;
import com.chuamgwei.module.auth.entity.AuthPrincipal;
import com.chuamgwei.module.auth.service.AuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 请求拦截器：从服务端签发的访问令牌中提取操作人信息放入 RequestContext。
 * 不做缺失拒绝判断，身份缺失由 AuthAspect 切面统一处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenService authTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token == null) {
            log.debug("请求进入: {} {}, 未携带访问令牌", request.getMethod(), request.getRequestURI());
            return true;
        }

        try {
            AuthPrincipal principal = authTokenService.parseToken(token);
            RequestContext.setOperatorUuid(principal.getUserUuid());
            RequestContext.setOperatorName(principal.getUsername());
            RequestContext.setAdminPermissions(principal.getAdminPermissions());
            log.debug("请求进入: {} {}, operator={}", request.getMethod(), request.getRequestURI(), principal.getUsername());
            return true;
        } catch (Exception e) {
            throw new AuthException("访问令牌无效或已过期");
        }
    }

    /**
     * 从 Authorization 头中提取 Bearer token
     */
    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        RequestContext.clear();
    }
}