package com.chuamgwei.module.recharge.payment;

import com.chuamgwei.module.recharge.entity.RechargePayType;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付通道注册表，按充值支付方式选择对应通道
 */
@Component
public class PaymentProviderRegistry {

    private final List<PaymentProvider> providers;

    public PaymentProviderRegistry(List<PaymentProvider> providers) {
        this.providers = providers;
    }

    /**
     * 按支付方式获取支付通道
     */
    public PaymentProvider getProvider(String payType) {
        String normalizedPayType = RechargePayType.normalize(payType);
        for (PaymentProvider provider : providers) {
            if (normalizedPayType.equalsIgnoreCase(provider.getType())) {
                return provider;
            }
        }
        throw new RuntimeException("未找到支付通道: " + normalizedPayType);
    }
}