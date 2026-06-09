package com.chuamgwei.module.redis.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditConsumeRecord;
import com.chuamgwei.module.credit.entity.CreditFlow;
import com.chuamgwei.module.credit.entity.CreditLogVO;

/**
 * 积分日志读缓存服务
 */
public interface CreditLogCacheService {

    /**
     * 获取缓存的用户消费记录分页
     */
    Page<CreditConsumeRecord> getCachedConsumeRecords(String userUuid, long current, long size);

    /**
     * 缓存用户消费记录分页
     */
    void cacheConsumeRecords(String userUuid, long current, long size, Page<CreditConsumeRecord> page);

    /**
     * 获取缓存的用户统一积分日志分页
     */
    Page<CreditLogVO> getCachedUserLogs(String userUuid, long current, long size, String type,
                                         String direction, String keyword, String startTime, String endTime);

    /**
     * 缓存用户统一积分日志分页
     */
    void cacheUserLogs(String userUuid, long current, long size, String type, String direction,
                       String keyword, String startTime, String endTime, Page<CreditLogVO> page);

    /**
     * 获取缓存的原始积分流水分页
     */
    Page<CreditFlow> getCachedFlows(long current, long size, String userUuid, String bizType);

    /**
     * 缓存原始积分流水分页
     */
    void cacheFlows(long current, long size, String userUuid, String bizType, Page<CreditFlow> page);

    /**
     * 推进用户维度日志缓存版本
     */
    void bumpUserVersion(String userUuid);

    /**
     * 推进全局日志缓存版本
     */
    void bumpGlobalVersion();
}