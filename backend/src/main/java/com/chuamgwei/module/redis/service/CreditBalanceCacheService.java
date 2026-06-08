package com.chuamgwei.module.redis.service;

import java.math.BigDecimal;

/**
 * 积分余额缓存服务
 */
public interface CreditBalanceCacheService {

    /**
     * 获取缓存的可用余额
     */
    BigDecimal getCachedBalance(String userUuid);

    /**
     * 缓存用户可用余额（带随机过期防雪崩）
     */
    void cacheBalance(String userUuid, BigDecimal availablePoints);

    /**
     * 删除用户余额缓存（余额变动时调用）
     */
    void evictBalance(String userUuid);
}