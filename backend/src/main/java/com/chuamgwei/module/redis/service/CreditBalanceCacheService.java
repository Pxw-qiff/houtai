package com.chuamgwei.module.redis.service;

import com.chuamgwei.module.credit.entity.CreditAccount;

/**
 * 积分账户缓存服务
 */
public interface CreditBalanceCacheService {

    /**
     * 获取缓存的积分账户
     */
    CreditAccount getCachedAccount(String userUuid);

    /**
     * 缓存积分账户（带随机过期防雪崩）
     */
    void cacheAccount(String userUuid, CreditAccount account);

    /**
     * 删除用户余额缓存（余额变动时调用）
     */
    void evictBalance(String userUuid);
}