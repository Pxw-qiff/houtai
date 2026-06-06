package com.chuamgwei.module.auth.entity;

import lombok.Data;

/**
 * 已通过服务端签名认证的当前用户身份
 */
@Data
public class AuthPrincipal {

    private String userUuid;

    private String username;

    private Integer adminPermissions;
}