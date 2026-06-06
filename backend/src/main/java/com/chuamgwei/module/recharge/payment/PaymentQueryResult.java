package com.chuamgwei.module.recharge.payment;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 通用支付查单结果
 */
@Data
public class PaymentQueryResult {

    private String providerType;

    private boolean querySuccess;

    private PaymentQueryStatus status;

    private String orderNo;

    private String providerTradeNo;

    private BigDecimal amount;

    private String rawStatus;

    private String providerCode;

    private String providerMessage;

    private String providerSubCode;

    private String providerSubMessage;

    private String failureMessage;

    /**
     * 判断第三方交易是否已经支付成功
     */
    public boolean isPaid() {
        return status == PaymentQueryStatus.PAID;
    }

    /**
     * 判断第三方交易是否仍在等待用户付款
     */
    public boolean isWaitingPay() {
        return status == PaymentQueryStatus.WAITING_PAY;
    }

    /**
     * 判断第三方交易是否已经关闭
     */
    public boolean isClosed() {
        return status == PaymentQueryStatus.CLOSED;
    }

    /**
     * 判断第三方平台是否暂未查到交易
     */
    public boolean isNotExist() {
        return status == PaymentQueryStatus.NOT_EXIST;
    }

    /**
     * 判断本次查单是否未返回可用业务结果
     */
    public boolean isQueryFailed() {
        return !querySuccess;
    }
}