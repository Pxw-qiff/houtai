package com.chuamgwei.module.recharge.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.recharge.config.AlipayProperties;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.entity.RechargeOrderStatus;
import com.chuamgwei.module.recharge.entity.RechargePayType;
import com.chuamgwei.module.recharge.mapper.RechargeOrderMapper;
import com.chuamgwei.module.recharge.payment.PaymentProviderRegistry;
import com.chuamgwei.module.recharge.service.RechargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 充值订单业务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeServiceImpl implements RechargeService {

    private final UserMapper userMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final RechargeOrderCompletionService rechargeOrderCompletionService;
    private final RechargeConfigService rechargeConfigService;
    private final RechargePrecreateService rechargePrecreateService;
    private final RechargeReconcileService rechargeReconcileService;
    private final PaymentProviderRegistry paymentProviderRegistry;
    private final AlipayProperties alipayProperties;

    @Override
    public BigDecimal getChargeRatio() {
        return rechargeConfigService.getChargeRatio();
    }

    @Override
    public Map<String, Object> getRechargeSettings() {
        return rechargeConfigService.getRechargeSettings();
    }

    @Override
    public Integer getPayTimeoutMinutes() {
        return rechargeConfigService.getPayTimeoutMinutes();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder createRechargeOrder(String userUuid, BigDecimal amount, String payType) {
        ensureRechargeUserAllowed(userUuid);
        BigDecimal normalizedAmount = RechargeAmountUtils.normalizeAmount(amount);
        String normalizedPayType = RechargePayType.normalize(payType);
        BigDecimal ratio = rechargeConfigService.getChargeRatio();
        BigDecimal points = RechargeAmountUtils.calculatePoints(normalizedAmount, ratio);

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo("ORD" + IdUtil.getSnowflakeNextIdStr());
        order.setUserUuid(userUuid);
        order.setAmount(normalizedAmount);
        order.setPoints(points);
        order.setChargeRatio(ratio);
        order.setPayType(normalizedPayType);
        order.setStatus(RechargeOrderStatus.CREATED);
        order.setExpireTime(null);

        rechargeOrderMapper.insert(order);
        log.info("用户 {} 创建充值订单: {}, 金额: {}元, 倍率: {}, 积分: {}",
                userUuid, order.getOrderNo(), normalizedAmount, ratio, points);
        return order;
    }

    /**
     * 校验充值目标用户必须存在且处于可用状态
     */
    private void ensureRechargeUserAllowed(String userUuid) {
        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new RuntimeException("用户身份不能为空");
        }
        User user = userMapper.selectById(userUuid);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + userUuid);
        }
        if (Integer.valueOf(1).equals(user.getIsDeleted())) {
            throw new RuntimeException("用户已删除，不能充值");
        }
        if (user.getIsBanned() != null && user.getIsBanned() != 0) {
            throw new RuntimeException("用户已封禁，不能充值");
        }
    }

    @Override
    public RechargeOrder getRechargeOrder(String orderNo) {
        RechargeOrder order = rechargeOrderMapper.selectOne(
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderNo);
        }
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handlePayCallback(String orderNo, String tradeNo, BigDecimal amount, String payStatus) {
        return rechargeOrderCompletionService.completePayCallback(orderNo, tradeNo, amount, payStatus);
    }

    @Override
    public String createAlipayQrCode(String orderNo) {
        return rechargePrecreateService.createAlipayQrCode(orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String handleAlipayCallback(Map<String, String> params) {
        if (!paymentProviderRegistry.getProvider(RechargePayType.ALIPAY).verifyCallback(params)) {
            log.error("支付宝回调验签不通过");
            return "fail";
        }

        String appId = params.get("app_id");
        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        String totalAmount = params.get("total_amount");
        String tradeStatus = params.get("trade_status");
        if (StrUtil.isBlank(appId) || StrUtil.isBlank(orderNo) || StrUtil.isBlank(tradeNo)
                || StrUtil.isBlank(totalAmount) || StrUtil.isBlank(tradeStatus)) {
            log.error("支付宝回调缺少必要参数: appId={}, orderNo={}, tradeNo={}, amount={}, status={}",
                    appId, orderNo, tradeNo, totalAmount, tradeStatus);
            return "fail";
        }

        if (!alipayProperties.getAppId().equals(appId)) {
            log.error("支付宝回调应用号不匹配: orderNo={}, appId={}", orderNo, appId);
            return "fail";
        }

        log.info("支付宝回调验签通过: orderNo={}, tradeNo={}, amount={}, status={}",
                orderNo, tradeNo, totalAmount, tradeStatus);

        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("支付宝交易未成功，跳过处理: orderNo={}, status={}", orderNo, tradeStatus);
            return "success";
        }

        BigDecimal amount;
        try {
            amount = RechargeAmountUtils.normalizeAmount(new BigDecimal(totalAmount));
        } catch (NumberFormatException e) {
            log.error("支付宝回调金额格式错误: orderNo={}, amount={}", orderNo, totalAmount);
            return "fail";
        }
        return handlePayCallback(orderNo, tradeNo, amount, "SUCCESS");
    }

    @Override
    public String reconcileAlipayOrder(String orderNo) {
        return rechargeReconcileService.reconcileAlipayOrder(orderNo);
    }

    @Override
    public int reconcilePendingAlipayOrders() {
        return rechargeReconcileService.reconcilePendingAlipayOrders();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChargeRatio(BigDecimal ratio, String operatorUuid, String operatorName) {
        return rechargeConfigService.updateChargeRatio(ratio, operatorUuid, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRechargeSettings(BigDecimal ratio, Integer payTimeoutMinutes, String paySubjectPrefix,
                                          String operatorUuid, String operatorName) {
        return rechargeConfigService.updateRechargeSettings(ratio, payTimeoutMinutes, paySubjectPrefix,
                operatorUuid, operatorName);
    }
}