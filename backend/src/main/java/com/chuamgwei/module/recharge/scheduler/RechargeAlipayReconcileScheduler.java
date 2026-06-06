package com.chuamgwei.module.recharge.scheduler;

import com.chuamgwei.module.recharge.service.RechargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付宝未支付订单主动查单补偿调度器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "recharge.reconcile.enabled", havingValue = "true", matchIfMissing = true)
public class RechargeAlipayReconcileScheduler {

    private final RechargeService rechargeService;

    /**
     * 定期补偿支付宝回调丢失或延迟导致的未到账订单
     */
    @Scheduled(
            initialDelayString = "${recharge.reconcile.initial-delay-ms:30000}",
            fixedDelayString = "${recharge.reconcile.fixed-delay-ms:60000}"
    )
    public void reconcilePendingAlipayOrders() {
        try {
            int changedCount = rechargeService.reconcilePendingAlipayOrders();
            if (changedCount > 0) {
                log.info("支付宝查单补偿产生状态变更: changedCount={}", changedCount);
            }
        } catch (Exception e) {
            log.error("支付宝查单补偿调度执行失败", e);
        }
    }
}