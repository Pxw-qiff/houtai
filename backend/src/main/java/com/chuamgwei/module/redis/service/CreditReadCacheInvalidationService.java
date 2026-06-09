package com.chuamgwei.module.redis.service;

/**
 * 积分读缓存失效服务
 */
public interface CreditReadCacheInvalidationService {

    /**
     * 在事务提交后失效积分相关读缓存
     */
    void evictAfterCommit(String userUuid);
}