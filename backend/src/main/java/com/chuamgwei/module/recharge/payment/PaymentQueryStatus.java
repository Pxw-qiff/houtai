package com.chuamgwei.module.recharge.payment;

/**
 * 通用支付查单状态
 */
public enum PaymentQueryStatus {

    /** 第三方交易等待付款 */
    WAITING_PAY,

    /** 第三方交易已支付 */
    PAID,

    /** 第三方交易已关闭 */
    CLOSED,

    /** 第三方平台暂未查到交易 */
    NOT_EXIST,

    /** 第三方返回了暂未识别的交易状态 */
    UNKNOWN,

    /** 第三方通道返回异常或不可判断结果 */
    GATEWAY_ERROR
}