package com.chuamgwei.module.credit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 内部积分计费接口配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "internal.credit")
public class InternalCreditProperties {

    /** 服务间调用密钥 */
    private String secret;
}