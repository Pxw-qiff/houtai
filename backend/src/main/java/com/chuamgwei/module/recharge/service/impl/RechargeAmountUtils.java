package com.chuamgwei.module.recharge.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 充值金额与积分计算工具
 */
public final class RechargeAmountUtils {

    private static final int MONEY_SCALE = 2;
    private static final int POINT_SCALE = 6;

    private RechargeAmountUtils() {
    }

    /**
     * 标准化充值金额
     */
    public static BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值金额必须大于零");
        }
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 按充值金额和兑换比例计算到账积分
     */
    public static BigDecimal calculatePoints(BigDecimal amount, BigDecimal ratio) {
        return amount.multiply(ratio).setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }
}