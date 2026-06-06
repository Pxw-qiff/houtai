package com.chuamgwei.module.recharge.entity;

/**
 * 充值支付方式常量与校验规则
 */
public final class RechargePayType {

    public static final String ALIPAY = "ALIPAY";

    private RechargePayType() {
    }

    /**
     * 标准化并校验支付方式
     */
    public static String normalize(String payType) {
        if (payType == null || payType.trim().isEmpty()) {
            throw new RuntimeException("支付方式不能为空");
        }
        String normalized = payType.trim().toUpperCase();
        if (!ALIPAY.equals(normalized)) {
            throw new RuntimeException("暂不支持的支付方式: " + payType);
        }
        return normalized;
    }

    /**
     * 判断是否为支付宝支付方式
     */
    public static boolean isAlipay(String payType) {
        return ALIPAY.equalsIgnoreCase(payType);
    }
}