package com.chuamgwei.module.recharge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.module.credit.service.CreditService;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.entity.RechargeOrderStatus;
import com.chuamgwei.module.recharge.mapper.RechargeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 充值订单支付完成事务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeOrderCompletionService {

    private final RechargeOrderMapper rechargeOrderMapper;
    private final CreditService creditService;
    private final PlatformTransactionManager transactionManager;

    /**
     * 幂等完成支付订单并执行积分入账
     */
    public String completePayCallback(String orderNo, String tradeNo, BigDecimal amount, String payStatus) {
        log.info("支付回调: 订单={}, 第三方单号={}, 金额={}, 状态={}", orderNo, tradeNo, amount, payStatus);

        CompletionDecision decision = executeInTransaction(
                TransactionDefinition.PROPAGATION_REQUIRED,
                () -> confirmPayment(orderNo, tradeNo, amount, payStatus)
        );
        if (!decision.shouldCredit()) {
            return "success";
        }

        RechargeOrder order = decision.getOrder();
        try {
            executeInTransaction(
                    TransactionDefinition.PROPAGATION_REQUIRED,
                    () -> {
                        creditAndMarkCredited(order);
                        return null;
                    }
            );
        } catch (Exception e) {
            markExceptionSafely(orderNo);
            log.error("充值支付已确认但积分入账异常: orderNo={}, tradeNo={}", orderNo, tradeNo, e);
            return "success";
        }

        log.info("充值到账成功: 用户={}, 积分={}", order.getUserUuid(), order.getPoints());
        return "success";
    }

    /**
     * 确认支付结果并决定是否继续积分入账
     */
    private CompletionDecision confirmPayment(String orderNo, String tradeNo, BigDecimal amount, String payStatus) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null) {
            log.error("订单 {} 不存在", orderNo);
            throw new RuntimeException("订单不存在");
        }

        if (RechargeOrderStatus.isCompleted(order.getStatus())) {
            log.info("订单 {} 已入账，幂等返回", orderNo);
            return CompletionDecision.none();
        }

        if (!isPaymentSuccess(payStatus)) {
            if (RechargeOrderStatus.canCompletePayment(order.getStatus())) {
                order.setStatus(RechargeOrderStatus.FAILED);
                rechargeOrderMapper.updateById(order);
            }
            return CompletionDecision.none();
        }

        if (!RechargeOrderStatus.canCompletePayment(order.getStatus())) {
            log.warn("订单状态不允许进入支付完成流程: orderNo={}, status={}", orderNo, order.getStatus());
            return CompletionDecision.none();
        }

        if (order.getAmount().compareTo(amount) != 0) {
            log.error("金额不一致: 订单={}, 回调={}", order.getAmount(), amount);
            throw new RuntimeException("支付金额不一致");
        }

        order.setStatus(RechargeOrderStatus.PAID);
        order.setTradeNo(tradeNo);
        order.setPayTime(LocalDateTime.now());
        rechargeOrderMapper.updateById(order);
        return CompletionDecision.credit(order);
    }

    /**
     * 在同一事务内完成积分入账和订单到账标记
     */
    private void creditAndMarkCredited(RechargeOrder order) {
        creditService.addPoints(order.getUserUuid(), order.getPoints(), order.getOrderNo(), "充值到账");

        RechargeOrder lockedOrder = selectOrderForUpdate(order.getOrderNo());
        if (lockedOrder == null) {
            throw new RuntimeException("订单不存在: " + order.getOrderNo());
        }
        if (RechargeOrderStatus.isCompleted(lockedOrder.getStatus())) {
            return;
        }
        if (!RechargeOrderStatus.equalsStatus(lockedOrder.getStatus(), RechargeOrderStatus.PAID)) {
            throw new RuntimeException("订单状态不允许标记到账: " + lockedOrder.getStatus());
        }

        lockedOrder.setStatus(RechargeOrderStatus.CREDITED);
        lockedOrder.setCreditedTime(LocalDateTime.now());
        rechargeOrderMapper.updateById(lockedOrder);
    }

    /**
     * 使用独立事务标记积分入账异常
     */
    private void markExceptionSafely(String orderNo) {
        try {
            executeInTransaction(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    () -> {
                        markException(orderNo);
                        return null;
                    }
            );
        } catch (Exception e) {
            log.error("充值订单异常状态落库失败: orderNo={}", orderNo, e);
        }
    }

    /**
     * 将已支付但未入账的订单标记为异常
     */
    private void markException(String orderNo) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null) {
            log.error("充值订单异常状态落库失败，订单不存在: orderNo={}", orderNo);
            return;
        }
        if (RechargeOrderStatus.isCompleted(order.getStatus())) {
            return;
        }
        if (!RechargeOrderStatus.equalsStatus(order.getStatus(), RechargeOrderStatus.PAID)) {
            log.warn("充值订单异常状态跳过，当前状态不允许标记异常: orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(RechargeOrderStatus.EXCEPTION);
        rechargeOrderMapper.updateById(order);
    }

    /**
     * 判断支付状态是否表示已经支付成功
     */
    private boolean isPaymentSuccess(String payStatus) {
        return "SUCCESS".equalsIgnoreCase(payStatus) || "PAID".equalsIgnoreCase(payStatus);
    }

    /**
     * 按指定传播行为执行事务片段
     */
    private <T> T executeInTransaction(int propagationBehavior, Supplier<T> callback) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagationBehavior);
        return template.execute(status -> callback.get());
    }

    /**
     * 支付回调处理决策
     */
    private static class CompletionDecision {
        private final boolean shouldCredit;
        private final RechargeOrder order;

        private CompletionDecision(boolean shouldCredit, RechargeOrder order) {
            this.shouldCredit = shouldCredit;
            this.order = order;
        }

        private static CompletionDecision none() {
            return new CompletionDecision(false, null);
        }

        private static CompletionDecision credit(RechargeOrder order) {
            return new CompletionDecision(true, order);
        }

        private boolean shouldCredit() {
            return shouldCredit;
        }

        private RechargeOrder getOrder() {
            return order;
        }
    }

    /**
     * 支付宝侧交易关闭时将未支付订单标记为超时
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markTimeoutIfUnpaid(String orderNo) {
        RechargeOrder order = rechargeOrderMapper.selectOne(
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getOrderNo, orderNo)
                        .last("for update")
        );
        if (order == null || !RechargeOrderStatus.canMarkTimeout(order.getStatus())) {
            return false;
        }
        order.setStatus(RechargeOrderStatus.TIMEOUT);
        rechargeOrderMapper.updateById(order);
        log.info("充值订单已标记超时: orderNo={}", orderNo);
        return true;
    }

    /**
     * 请求支付通道前先占位，避免同一订单并发重复预下单
     */
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrder markPrecreating(String orderNo) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null) {
            throw new RuntimeException("订单不存在: " + orderNo);
        }
        if (!RechargeOrderStatus.canStartPrecreate(order.getStatus())) {
            throw new RuntimeException("订单状态不允许支付: " + order.getStatus());
        }
        order.setStatus(RechargeOrderStatus.PRECREATING);
        rechargeOrderMapper.updateById(order);
        log.info("充值订单已进入预下单中: orderNo={}", orderNo);
        return order;
    }

    /**
     * 支付通道返回二维码后标记订单进入可支付状态
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markWaitPay(String orderNo, LocalDateTime expireTime) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null || !RechargeOrderStatus.canPrecreate(order.getStatus())) {
            return false;
        }
        order.setStatus(RechargeOrderStatus.WAIT_PAY);
        order.setExpireTime(expireTime);
        rechargeOrderMapper.updateById(order);
        log.info("充值订单已进入待支付状态: orderNo={}, expireTime={}", orderNo, expireTime);
        return true;
    }

    /**
     * 支付通道请求超时或异常时标记预下单状态未知
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPrecreateUnknown(String orderNo) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null || !RechargeOrderStatus.canMarkPrecreateFailure(order.getStatus())) {
            return false;
        }
        order.setStatus(RechargeOrderStatus.PRECREATE_UNKNOWN);
        rechargeOrderMapper.updateById(order);
        log.warn("充值订单预下单状态未知: orderNo={}", orderNo);
        return true;
    }

    /**
     * 支付通道明确拒绝预下单时标记失败
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPrecreateFailed(String orderNo) {
        RechargeOrder order = selectOrderForUpdate(orderNo);
        if (order == null || !RechargeOrderStatus.canMarkPrecreateFailure(order.getStatus())) {
            return false;
        }
        order.setStatus(RechargeOrderStatus.PRECREATE_FAILED);
        rechargeOrderMapper.updateById(order);
        log.warn("充值订单预下单失败: orderNo={}", orderNo);
        return true;
    }

    /**
     * 使用行锁读取充值订单
     */
    private RechargeOrder selectOrderForUpdate(String orderNo) {
        return rechargeOrderMapper.selectOne(
                Wrappers.<RechargeOrder>lambdaQuery()
                        .eq(RechargeOrder::getOrderNo, orderNo)
                        .last("for update")
        );
    }
}