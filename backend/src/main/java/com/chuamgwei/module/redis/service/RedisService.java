package com.chuamgwei.module.redis.service;

import java.time.Duration;

/**
 * Redis 统一调用入口，业务模块不要直接依赖底层 Redis 客户端
 */
public interface RedisService {

    /**
     * 读取字符串缓存
     */
    String get(String key);

    /**
     * 写入永久字符串缓存
     */
    void set(String key, String value);

    /**
     * 写入带过期时间的字符串缓存
     */
    void set(String key, String value, Duration ttl);

    /**
     * 仅在键不存在时写入带过期时间的字符串缓存
     */
    boolean setIfAbsent(String key, String value, Duration ttl);

    /**
     * 取出字符串缓存并删除原键
     */
    String take(String key);

    /**
     * 删除指定缓存键
     */
    boolean delete(String key);

    /**
     * 设置指定缓存键的过期时间
     */
    boolean expire(String key, Duration ttl);

    /**
     * 自增计数并在首次创建时设置过期时间
     */
    long increment(String key, Duration firstTtl);
}