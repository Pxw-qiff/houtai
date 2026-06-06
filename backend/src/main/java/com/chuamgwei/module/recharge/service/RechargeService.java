package com.chuamgwei.module.recharge.service;

import com.chuamgwei.module.recharge.entity.RechargeOrder;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 充值订单业务接口
 */
public interface RechargeService {

    /**
     * 获取系统当前的充值兑换比例（1元 = 多少积分）
     */
    BigDecimal getChargeRatio();

    /**
     * 获取充值管理配置
     */
    Map<String, Object> getRechargeSettings();

    /**
     * 获取支付超时时间（分钟）
     */
    Integer getPayTimeoutMinutes();

    /**
     * 创建充值订单
     */
    RechargeOrder createRechargeOrder(String userUuid, BigDecimal amount, String payType);

    /**
     * 按订单号查询充值订单
     */
    RechargeOrder getRechargeOrder(String orderNo);

    /**
     * 生成支付宝扫码支付二维码内容
     */
    String createAlipayQrCode(String orderNo);

    /**
     * 处理支付宝异步回调（验签 + 入账）
     */
    String handleAlipayCallback(Map<String, String> params);

    /**
     * 处理支付回调（幂等）
     */
    String handlePayCallback(String orderNo, String tradeNo, BigDecimal amount, String payStatus);

    /**
     * 按订单号执行支付宝查单补偿
     */
    String reconcileAlipayOrder(String orderNo);

    /**
     * 批量执行支付宝未支付订单查单补偿
     */
    int reconcilePendingAlipayOrders();

    /**
     * 修改充值兑换比例
     */
    boolean updateChargeRatio(BigDecimal ratio, String operatorUuid, String operatorName);

    /**
     * 修改充值管理配置
     */
    boolean updateRechargeSettings(BigDecimal ratio, Integer payTimeoutMinutes, String paySubjectPrefix,
                                   String operatorUuid, String operatorName);
}