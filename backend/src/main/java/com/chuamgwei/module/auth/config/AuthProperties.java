package com.chuamgwei.module.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

/**
 * 积分系统认证配置
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 签名密钥 */
    @NotBlank(message = "认证JWT密钥不能为空")
    private String jwtSecret;

    /** JWT 有效期分钟数 */
    @Min(value = 1, message = "认证JWT有效期不能小于1分钟")
    private Integer jwtExpireMinutes = 720;

    /** 历史兼容的一次性 ticket 前缀，不作为普通用户主登录链路 */
    @NotBlank(message = "认证ticket前缀不能为空")
    private String ticketPrefix = "ticket:";
}