package com.chuamgwei.module.recharge.payment;

/**
 * 第三方支付平台交易可见性状态（通用）
 * 用于预下单后确认第三方平台是否已登记本地订单号
 */
public enum TradeVisibilityStatus {
    
    /** 交易已存在且等待付款 */
    WAITING_PAY,
    
    /** 交易已支付成功 */
    PAID,
    
    /** 交易已关闭 */
    CLOSED,
    
    /** 交易不存在（第三方平台查不到该订单号） */
    NOT_EXIST,
    
    /** 通道异常（网络错误、SDK异常等） */
    GATEWAY_ERROR;
    
    /**
     * 判断是否为"交易已可见且等待付款"状态
     */
    public boolean isVisibleAndWaiting() {
        return this == WAITING_PAY;
    }
}