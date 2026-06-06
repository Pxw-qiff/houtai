package com.chuamgwei.module.recharge.payment;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付渠道抽象接口，后续扩展微信/Stripe 实现同一套方法
 */
public interface PaymentProvider {

    /** 支付类型标识，对应 recharge_orders.pay_type */
    String getType();

    /** 生成扫码支付二维码内容 */
    String createQrCode(String orderNo, BigDecimal amount, String subject, int timeoutMinutes);

    /** 查询支付订单状态 */
    PaymentQueryResult queryOrder(String orderNo);

    /** 验签异步回调参数 */
    boolean verifyCallback(Map<String, String> params);

    /**
     * 预下单后确认第三方平台是否已登记本地订单号
     * 最多查询3次，每次间隔300ms
     * 只确认订单可见性，不负责入账和状态变更
     *
     * @param orderNo 本地订单号
     * @return 交易可见性状态
     */
    TradeVisibilityStatus ensureTradeVisible(String orderNo);
}