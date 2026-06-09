package com.chuamgwei.module.redis.service.impl;

import cn.hutool.core.util.StrUtil;
import com.chuamgwei.module.redis.service.CreditBalanceCacheService;
import com.chuamgwei.module.redis.service.CreditLogCacheService;
import com.chuamgwei.module.redis.service.CreditReadCacheInvalidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 积分读缓存失效实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditReadCacheInvalidationServiceImpl implements CreditReadCacheInvalidationService {

    private final CreditBalanceCacheService creditBalanceCacheService;
    private final CreditLogCacheService creditLogCacheService;

    @Override
    public void evictAfterCommit(String userUuid) {
        String normalizedUserUuid = normalizeUserUuid(userUuid);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictNow(normalizedUserUuid);
                }
            });
            return;
        }
        evictNow(normalizedUserUuid);
    }

    /**
     * 立即失效积分相关读缓存
     */
    private void evictNow(String userUuid) {
        try {
            creditBalanceCacheService.evictBalance(userUuid);
        } catch (Exception e) {
            log.warn("删除积分余额缓存失败: userUuid={}, reason={}", userUuid, e.getMessage());
        }
        try {
            creditLogCacheService.bumpUserVersion(userUuid);
            creditLogCacheService.bumpGlobalVersion();
        } catch (Exception e) {
            log.warn("推进积分日志缓存版本失败: userUuid={}, reason={}", userUuid, e.getMessage());
        }
    }

    /**
     * 标准化用户UUID
     */
    private String normalizeUserUuid(String userUuid) {
        if (StrUtil.isBlank(userUuid)) {
            throw new IllegalArgumentException("userUuid不能为空");
        }
        return userUuid.trim();
    }
}