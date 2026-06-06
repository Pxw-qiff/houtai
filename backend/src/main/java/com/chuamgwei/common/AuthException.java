package com.chuamgwei.common;

/**
 * 身份校验异常，由 AuthAspect 在未携带身份凭证时抛出
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}