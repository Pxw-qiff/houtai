package com.chuamgwei.module.recharge.service.impl;

import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.entity.RechargePayType;
import com.chuamgwei.module.recharge.payment.PaymentGatewayException;
import com.chuamgwei.module.recharge.payment.PaymentProvider;
import com.chuamgwei.module.recharge.payment.PaymentProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 充值支付预下单编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargePrecreateService {

    private final RechargeOrderCompletionService rechargeOrderCompletionService;
    private final RechargeConfigService rechargeConfigService;
    private final PaymentProviderRegistry paymentProviderRegistry;

    /**
     * 生成支付宝扫码支付二维码内容
     */
    public String createAlipayQrCode(String orderNo) {
        RechargeOrder order = markAlipayPrecreating(orderNo);
        PaymentProvider paymentProvider = paymentProviderRegistry.getProvider(order.getPayType());
        try {
            int payTimeoutMinutes = rechargeConfigService.getPayTimeoutMinutes();
            String qrCode = paymentProvider.createQrCode(
                    orderNo, order.getAmount(), buildPaySubject(), payTimeoutMinutes);
            rechargeOrderCompletionService.markWaitPay(orderNo, LocalDateTime.now().plusMinutes(payTimeoutMinutes));
            log.info("支付宝预下单二维码已生成，订单进入待支付: orderNo={}", orderNo);
            return qrCode;
        } catch (PaymentGatewayException e) {
            markPrecreateFailure(orderNo, e);
            throw e;
        }
    }

    /**
     * 标记支付宝订单进入预下单中，并校验支付方式匹配
     */
    private RechargeOrder markAlipayPrecreating(String orderNo) {
        RechargeOrder order = rechargeOrderCompletionService.markPrecreating(orderNo);
        if (!RechargePayType.isAlipay(order.getPayType())) {
            markPrecreateFailure(orderNo, PaymentGatewayException.definite("订单支付方式不是支付宝"));
            throw new RuntimeException("订单支付方式不允许支付宝预下单: " + order.getPayType());
        }
        return order;
    }

    /**
     * 根据支付通道异常类型记录预下单失败状态
     */
    private void markPrecreateFailure(String orderNo, PaymentGatewayException e) {
        if (e.isUncertain()) {
            rechargeOrderCompletionService.markPrecreateUnknown(orderNo);
            return;
        }
        rechargeOrderCompletionService.markPrecreateFailed(orderNo);
    }

    /**
     * 生成支付宝账单展示名称
     */
    private String buildPaySubject() {
        return rechargeConfigService.getPaySubjectPrefix();
    }
}