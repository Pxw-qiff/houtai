package com.chuamgwei.module.redis.service.impl;

import com.chuamgwei.module.redis.service.CreditBalanceCacheService;
import com.chuamgwei.module.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 积分余额缓存实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditBalanceCacheServiceImpl implements CreditBalanceCacheService {

    private static final String KEY_PREFIX = "credit_balance:";
    private static final long BASE_TTL_SECONDS = 300L; // 5 分钟基准
    private static final long RANDOM_TTL_SECONDS = 60L; // 0-60 秒随机，防雪崩

    private final RedisService redisService;

    @Override
    public BigDecimal getCachedBalance(String userUuid) {
        String key = buildKey(userUuid);
        String cached = redisService.get(key);
        if (cached == null || cached.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(cached);
        } catch (NumberFormatException e) {
            log.warn("余额缓存格式错误: userUuid={}, cached={}", userUuid, cached);
            redisService.delete(key);
            return null;
        }
    }

    @Override
    public void cacheBalance(String userUuid, BigDecimal availablePoints) {
        if (availablePoints == null) {
            return;
        }
        String key = buildKey(userUuid);
        long randomSeconds = ThreadLocalRandom.current().nextLong(0, RANDOM_TTL_SECONDS);
        Duration ttl = Duration.ofSeconds(BASE_TTL_SECONDS + randomSeconds);
        redisService.set(key, availablePoints.toPlainString(), ttl);
        log.debug("缓存用户余额: userUuid={}, availablePoints={}, ttl={}s", 
                userUuid, availablePoints, BASE_TTL_SECONDS + randomSeconds);
    }

    @Override
    public void evictBalance(String userUuid) {
        String key = buildKey(userUuid);
        boolean deleted = redisService.delete(key);
        log.debug("删除用户余额缓存: userUuid={}, deleted={}", userUuid, deleted);
    }

    /**
     * 构建 Redis key
     */
    private String buildKey(String userUuid) {
        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new IllegalArgumentException("userUuid不能为空");
        }
        return KEY_PREFIX + userUuid.trim();
    }
}