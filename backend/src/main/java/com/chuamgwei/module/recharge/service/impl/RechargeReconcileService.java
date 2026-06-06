package com.chuamgwei.module.recharge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.entity.RechargeOrderStatus;
import com.chuamgwei.module.recharge.entity.RechargePayType;
import com.chuamgwei.module.recharge.mapper.RechargeOrderMapper;
import com.chuamgwei.module.recharge.payment.PaymentGatewayException;
import com.chuamgwei.module.recharge.payment.PaymentProviderRegistry;
import com.chuamgwei.module.recharge.payment.PaymentQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 充值订单查单补偿服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeReconcileService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final RechargeOrderCompletionService rechargeOrderCompletionService;
    private final RechargeConfigService rechargeConfigService;
    private final PaymentProviderRegistry paymentProviderRegistry;

    /**
     * 按订单号执行支付宝查单补偿
     */
    public String reconcileAlipayOrder(String orderNo) {
        ReconcileOutcome outcome = reconcileAlipayOrderInternal(orderNo);
        return outcome.getMessage();
    }

    /**
     * 批量执行支付宝未支付订单查单补偿
     */
    public int reconcilePendingAlipayOrders() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(1);
        List<RechargeOrder> orders = rechargeOrderMapper.selectList(
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getPayType, RechargePayType.ALIPAY)
                        .in(RechargeOrder::getStatus,
                                RechargeOrderStatus.CREATED,
                                RechargeOrderStatus.PRECREATING,
                                RechargeOrderStatus.WAIT_PAY,
                                RechargeOrderStatus.PRECREATE_UNKNOWN)
                        .le(RechargeOrder::getUpdatedAt, cutoffTime)
                        .orderByAsc(RechargeOrder::getUpdatedAt)
                        .last("limit 50")
        );

        int changedCount = 0;
        for (RechargeOrder order : orders) {
            try {
                ReconcileOutcome outcome = reconcileAlipayOrderInternal(order.getOrderNo());
                if (outcome.isChanged()) {
                    changedCount++;
                }
            } catch (PaymentGatewayException e) {
                log.warn("支付宝查单通道异常，等待下次补偿: orderNo={}, message={}", order.getOrderNo(), e.getMessage());
            } catch (Exception e) {
                log.error("支付宝查单补偿失败: orderNo={}", order.getOrderNo(), e);
            }
        }
        if (!orders.isEmpty()) {
            log.info("支付宝查单补偿完成: scanned={}, changed={}", orders.size(), changedCount);
        }
        return changedCount;
    }

    /**
     * 执行单笔支付宝查单补偿并返回处理结果
     */
    private ReconcileOutcome reconcileAlipayOrderInternal(String orderNo) {
        RechargeOrder order = rechargeOrderMapper.selectOne(
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getOrderNo, orderNo)
        );
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderNo);
        }
        if (!RechargePayType.isAlipay(order.getPayType())) {
            return ReconcileOutcome.skipped("非支付宝订单，跳过查单补偿");
        }
        if (isAlreadyCompleted(order.getStatus())) {
            return ReconcileOutcome.skipped("订单已完成，跳过查单补偿: " + order.getStatus());
        }

        PaymentQueryResult result = paymentProviderRegistry.getProvider(order.getPayType()).queryOrder(orderNo);
        if (result.isQueryFailed()) {
            if (isExpired(order)) {
                boolean changed = rechargeOrderCompletionService.markTimeoutIfUnpaid(orderNo);
                return changed
                        ? ReconcileOutcome.changed("支付宝查单未成功且本地订单已过期，已标记超时")
                        : ReconcileOutcome.skipped("支付宝查单未成功，订单状态已变化");
            }
            return ReconcileOutcome.skipped("支付宝查单未成功: " + failureMessage(result));
        }

        if (result.isPaid()) {
            if (result.getOrderNo() != null && !orderNo.equals(result.getOrderNo())) {
                throw new RuntimeException("支付宝查单返回订单号不一致: " + orderNo);
            }
            if (result.getProviderTradeNo() == null || result.getProviderTradeNo().trim().isEmpty()) {
                throw new RuntimeException("支付宝查单已支付但未返回 tradeNo: " + orderNo);
            }
            if (result.getAmount() == null) {
                throw new RuntimeException("支付宝查单已支付但未返回 totalAmount: " + orderNo);
            }
            rechargeOrderCompletionService.completePayCallback(
                    orderNo, result.getProviderTradeNo(), RechargeAmountUtils.normalizeAmount(result.getAmount()), "SUCCESS");
            return ReconcileOutcome.changed("支付宝查单确认已支付，已完成入账");
        }

        if (result.isClosed()) {
            boolean changed = rechargeOrderCompletionService.markTimeoutIfUnpaid(orderNo);
            return changed
                    ? ReconcileOutcome.changed("支付宝交易已关闭，已标记超时")
                    : ReconcileOutcome.skipped("支付宝交易已关闭，订单状态已变化");
        }

        if (result.isWaitingPay() && isExpired(order)) {
            boolean changed = rechargeOrderCompletionService.markTimeoutIfUnpaid(orderNo);
            return changed
                    ? ReconcileOutcome.changed("支付宝仍等待付款但本地订单已过期，已标记超时")
                    : ReconcileOutcome.skipped("支付宝仍等待付款，订单状态已变化");
        }

        return ReconcileOutcome.skipped("支付宝交易未到终态: " + result.getStatus() + ", rawStatus=" + result.getRawStatus());
    }

    /**
     * 判断订单是否已经完成支付或入账
     */
    private boolean isAlreadyCompleted(String status) {
        return RechargeOrderStatus.isCompleted(status);
    }

    /**
     * 判断订单是否已经超过本地支付有效期
     */
    private boolean isExpired(RechargeOrder order) {
        if (order.getExpireTime() != null) {
            return !LocalDateTime.now().isBefore(order.getExpireTime());
        }
        if (order.getCreatedAt() == null) {
            return false;
        }
        return !LocalDateTime.now().isBefore(order.getCreatedAt().plusMinutes(rechargeConfigService.getPayTimeoutMinutes()));
    }

    /**
     * 获取通用查单失败消息
     */
    private String failureMessage(PaymentQueryResult result) {
        if (result.getFailureMessage() != null && !result.getFailureMessage().trim().isEmpty()) {
            return result.getFailureMessage();
        }
        if (result.getProviderSubMessage() != null && !result.getProviderSubMessage().trim().isEmpty()) {
            return result.getProviderSubMessage();
        }
        if (result.getProviderMessage() != null && !result.getProviderMessage().trim().isEmpty()) {
            return result.getProviderMessage();
        }
        if (result.getProviderSubCode() != null && !result.getProviderSubCode().trim().isEmpty()) {
            return result.getProviderSubCode();
        }
        return result.getProviderCode();
    }

    /**
     * 单笔补偿结果
     */
    private static class ReconcileOutcome {
        private final boolean changed;
        private final String message;

        private ReconcileOutcome(boolean changed, String message) {
            this.changed = changed;
            this.message = message;
        }

        /**
         * 创建已产生状态变更的补偿结果
         */
        private static ReconcileOutcome changed(String message) {
            return new ReconcileOutcome(true, message);
        }

        /**
         * 创建未产生状态变更的补偿结果
         */
        private static ReconcileOutcome skipped(String message) {
            return new ReconcileOutcome(false, message);
        }

        /**
         * 判断补偿是否产生状态变更
         */
        private boolean isChanged() {
            return changed;
        }

        /**
         * 获取补偿结果消息
         */
        private String getMessage() {
            return message;
        }
    }
}