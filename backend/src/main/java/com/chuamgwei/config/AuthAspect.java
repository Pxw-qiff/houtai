package com.chuamgwei.config;

import com.chuamgwei.common.AuthException;
import com.chuamgwei.common.NoAuth;
import com.chuamgwei.common.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 身份校验切面：拦截 Controller 方法，检查是否标注了 @NoAuth，
 * 未标注且 RequestContext 中无操作人身份时拒绝请求。
 */
@Slf4j
@Aspect
@Component
public class AuthAspect {

    /** 切点：所有 Controller 的 public 方法 */
    @Before("execution(public * com.chuamgwei.module..controller..*.*(..))")
    public void checkAuth(JoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();

        // 标注了 @NoAuth 的方法跳过校验
        if (method.isAnnotationPresent(NoAuth.class)) {
            log.debug("切面放行（@NoAuth）: {}", method.getName());
            return;
        }

        String operatorUuid = RequestContext.getOperatorUuid();
        if (operatorUuid == null || operatorUuid.isEmpty()) {
            log.warn("身份校验未通过: {}.{}", joinPoint.getTarget().getClass().getSimpleName(), method.getName());
            throw new AuthException("未提供操作人身份凭证");
        }

        log.debug("身份校验通过: operator={}", RequestContext.getOperatorName());
    }
}