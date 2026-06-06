package com.chuamgwei.module.recharge.payment;

/**
 * 支付通道调用异常，用于区分明确失败和网关状态未知
 */
public class PaymentGatewayException extends RuntimeException {

    private final boolean uncertain;

    public PaymentGatewayException(String message, boolean uncertain, Throwable cause) {
        super(message, cause);
        this.uncertain = uncertain;
    }

    /**
     * 创建支付通道状态未知异常
     */
    public static PaymentGatewayException uncertain(String message, Throwable cause) {
        return new PaymentGatewayException(message, true, cause);
    }

    /**
     * 创建支付通道明确失败异常
     */
    public static PaymentGatewayException definite(String message) {
        return new PaymentGatewayException(message, false, null);
    }

    /**
     * 判断本次支付通道请求结果是否未知
     */
    public boolean isUncertain() {
        return uncertain;
    }
}