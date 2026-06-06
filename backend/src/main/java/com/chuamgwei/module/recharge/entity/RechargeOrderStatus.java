package com.chuamgwei.module.recharge.entity;

/**
 * 充值订单状态常量与状态判断规则
 */
public final class RechargeOrderStatus {

    /** 本地订单已创建，尚未成功获取支付二维码 */
    public static final String CREATED = "CREATED";

    /** 正在请求支付通道预下单，防止同一订单并发重复请求 */
    public static final String PRECREATING = "PRECREATING";

    /** 已成功获取支付二维码，等待用户扫码付款 */
    public static final String WAIT_PAY = "WAIT_PAY";

    /** 支付通道请求超时或异常，无法确认支付宝侧是否创建订单 */
    public static final String PRECREATE_UNKNOWN = "PRECREATE_UNKNOWN";

    /** 支付通道明确拒绝预下单，订单仍可在修复配置后重试 */
    public static final String PRECREATE_FAILED = "PRECREATE_FAILED";

    /** 支付通道已确认付款成功，等待积分入账 */
    public static final String PAID = "PAID";

    /** 支付成功且积分已完成入账 */
    public static final String CREDITED = "CREDITED";

    /** 支付明确失败 */
    public static final String FAILED = "FAILED";

    /** 订单超过有效期仍未完成支付 */
    public static final String TIMEOUT = "TIMEOUT";

    /** 订单被主动取消 */
    public static final String CANCELLED = "CANCELLED";

    /** 支付已确认但积分入账过程异常，需要人工介入 */
    public static final String EXCEPTION = "EXCEPTION";

    private RechargeOrderStatus() {
    }

    /**
     * 判断订单是否已经最终完成入账
     */
    public static boolean isCompleted(String status) {
        return equalsStatus(status, CREDITED);
    }

    /**
     * 判断订单是否允许开始请求支付通道预下单
     */
    public static boolean canStartPrecreate(String status) {
        return equalsStatus(status, CREATED)
                || equalsStatus(status, WAIT_PAY)
                || equalsStatus(status, PRECREATE_UNKNOWN)
                || equalsStatus(status, PRECREATE_FAILED);
    }

    /**
     * 判断订单是否允许再次发起支付预下单
     */
    public static boolean canPrecreate(String status) {
        return canStartPrecreate(status) || equalsStatus(status, PRECREATING);
    }

    /**
     * 判断订单是否应该进入批量查单补偿范围
     */
    public static boolean canBatchReconcile(String status) {
        return equalsStatus(status, CREATED)
                || equalsStatus(status, PRECREATING)
                || equalsStatus(status, WAIT_PAY)
                || equalsStatus(status, PRECREATE_UNKNOWN);
    }

    /**
     * 判断订单是否允许被标记为支付超时
     */
    public static boolean canMarkTimeout(String status) {
        return equalsStatus(status, CREATED)
                || equalsStatus(status, PRECREATING)
                || equalsStatus(status, WAIT_PAY)
                || equalsStatus(status, PRECREATE_UNKNOWN);
    }

    /**
     * 判断订单是否允许记录预下单失败状态
     */
    public static boolean canMarkPrecreateFailure(String status) {
        return equalsStatus(status, CREATED)
                || equalsStatus(status, PRECREATING)
                || equalsStatus(status, PRECREATE_UNKNOWN)
                || equalsStatus(status, PRECREATE_FAILED);
    }

    /**
     * 判断支付成功通知是否允许进入入账流程
     */
    public static boolean canCompletePayment(String status) {
        return equalsStatus(status, CREATED)
                || equalsStatus(status, PRECREATING)
                || equalsStatus(status, WAIT_PAY)
                || equalsStatus(status, PRECREATE_UNKNOWN)
                || equalsStatus(status, PRECREATE_FAILED)
                || equalsStatus(status, PAID);
    }

    /**
     * 忽略大小写比较订单状态
     */
    public static boolean equalsStatus(String actual, String expected) {
        return expected != null && expected.equalsIgnoreCase(actual);
    }
}