package com.chuamgwei.module.recharge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;

/**
 * 支付宝支付配置属性
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "alipay")
public class AlipayProperties {

    /** 支付宝应用 APPID */
    @NotBlank(message = "支付宝应用APPID不能为空")
    private String appId;

    /** 应用私钥 (PKCS8) */
    @NotBlank(message = "支付宝应用私钥不能为空")
    private String privateKey;

    /** 支付宝公钥 */
    @NotBlank(message = "支付宝公钥不能为空")
    private String alipayPublicKey;

    /** 支付宝网关地址 */
    @NotBlank(message = "支付宝网关地址不能为空")
    private String serverUrl;

    /** 异步通知回调地址 (外网可达) */
    @NotBlank(message = "支付宝异步通知回调地址不能为空")
    private String notifyUrl;
}