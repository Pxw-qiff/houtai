package com.chuamgwei.module.redis.service.impl;

import com.chuamgwei.module.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Redis 字符串能力封装，统一处理键和值的基础校验
 */
@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(requireKey(key));
    }

    @Override
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(requireKey(key), requireValue(value));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(requireKey(key), requireValue(value), requirePositiveTtl(ttl));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(
                requireKey(key),
                requireValue(value),
                requirePositiveTtl(ttl)
        );
        return Boolean.TRUE.equals(result);
    }

    @Override
    public String take(String key) {
        return stringRedisTemplate.opsForValue().getAndDelete(requireKey(key));
    }

    @Override
    public boolean delete(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.delete(requireKey(key)));
    }

    @Override
    public boolean expire(String key, Duration ttl) {
        return Boolean.TRUE.equals(stringRedisTemplate.expire(requireKey(key), requirePositiveTtl(ttl)));
    }

    @Override
    public long increment(String key, Duration firstTtl) {
        String redisKey = requireKey(key);
        Long count = stringRedisTemplate.opsForValue().increment(redisKey);
        if (Long.valueOf(1L).equals(count) && firstTtl != null && !firstTtl.isZero() && !firstTtl.isNegative()) {
            stringRedisTemplate.expire(redisKey, firstTtl);
        }
        return count == null ? 0L : count;
    }

    /**
     * 校验 Redis key 不为空
     */
    private String requireKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("Redis key不能为空");
        }
        return key.trim();
    }

    /**
     * 校验 Redis value 不为 null
     */
    private String requireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Redis value不能为null");
        }
        return value;
    }

    /**
     * 校验 Redis 过期时间必须大于 0
     */
    private Duration requirePositiveTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Redis过期时间必须大于0");
        }
        return ttl;
    }
}