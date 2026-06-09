package com.chuamgwei.module.redis.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditConsumeRecord;
import com.chuamgwei.module.credit.entity.CreditFlow;
import com.chuamgwei.module.credit.entity.CreditLogVO;
import com.chuamgwei.module.redis.service.CreditLogCacheService;
import com.chuamgwei.module.redis.service.RedisService;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 积分日志读缓存实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditLogCacheServiceImpl implements CreditLogCacheService {

    private static final String KEY_PREFIX = "credit_log:";
    private static final String USER_VERSION_PREFIX = KEY_PREFIX + "version:user:";
    private static final String GLOBAL_VERSION_KEY = KEY_PREFIX + "version:global";
    private static final long CACHE_BASE_TTL_SECONDS = 30L;
    private static final long CACHE_RANDOM_TTL_SECONDS = 15L;
    private static final Duration VERSION_TTL = Duration.ofDays(7);

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<CreditConsumeRecord> getCachedConsumeRecords(String userUuid, long current, long size) {
        return readPage(buildConsumeRecordsKey(userUuid, current, size), CreditConsumeRecord.class);
    }

    @Override
    public void cacheConsumeRecords(String userUuid, long current, long size, Page<CreditConsumeRecord> page) {
        writePage(buildConsumeRecordsKey(userUuid, current, size), page);
    }

    @Override
    public Page<CreditLogVO> getCachedUserLogs(String userUuid, long current, long size, String type,
                                                String direction, String keyword, String startTime, String endTime) {
        return readPage(buildUserLogsKey(userUuid, current, size, type, direction, keyword, startTime, endTime), CreditLogVO.class);
    }

    @Override
    public void cacheUserLogs(String userUuid, long current, long size, String type, String direction,
                              String keyword, String startTime, String endTime, Page<CreditLogVO> page) {
        writePage(buildUserLogsKey(userUuid, current, size, type, direction, keyword, startTime, endTime), page);
    }

    @Override
    public Page<CreditFlow> getCachedFlows(long current, long size, String userUuid, String bizType) {
        return readPage(buildFlowsKey(current, size, userUuid, bizType), CreditFlow.class);
    }

    @Override
    public void cacheFlows(long current, long size, String userUuid, String bizType, Page<CreditFlow> page) {
        writePage(buildFlowsKey(current, size, userUuid, bizType), page);
    }

    @Override
    public void bumpUserVersion(String userUuid) {
        String normalizedUserUuid = requireUserUuid(userUuid);
        String key = USER_VERSION_PREFIX + normalizedUserUuid;
        try {
            long version = redisService.increment(key, VERSION_TTL);
            redisService.expire(key, VERSION_TTL);
            log.debug("推进用户积分日志缓存版本: userUuid={}, version={}", normalizedUserUuid, version);
        } catch (Exception e) {
            log.warn("推进用户积分日志缓存版本失败: userUuid={}, reason={}", normalizedUserUuid, e.getMessage());
        }
    }

    @Override
    public void bumpGlobalVersion() {
        try {
            long version = redisService.increment(GLOBAL_VERSION_KEY, VERSION_TTL);
            redisService.expire(GLOBAL_VERSION_KEY, VERSION_TTL);
            log.debug("推进全局积分日志缓存版本: version={}", version);
        } catch (Exception e) {
            log.warn("推进全局积分日志缓存版本失败: reason={}", e.getMessage());
        }
    }

    /**
     * 构建用户消费记录缓存键
     */
    private String buildConsumeRecordsKey(String userUuid, long current, long size) {
        String normalizedUserUuid = requireUserUuid(userUuid);
        String raw = String.join("|", "consume", normalizedUserUuid, getUserVersion(normalizedUserUuid),
                String.valueOf(current), String.valueOf(size));
        return KEY_PREFIX + "consume:" + normalizedUserUuid + ":" + digest(raw);
    }

    /**
     * 构建用户统一日志缓存键
     */
    private String buildUserLogsKey(String userUuid, long current, long size, String type, String direction,
                                    String keyword, String startTime, String endTime) {
        String normalizedUserUuid = requireUserUuid(userUuid);
        String raw = String.join("|", "user_logs", normalizedUserUuid, getUserVersion(normalizedUserUuid),
                String.valueOf(current), String.valueOf(size), cachePart(type), cachePart(direction),
                cachePart(keyword), cachePart(startTime), cachePart(endTime));
        return KEY_PREFIX + "user_logs:" + normalizedUserUuid + ":" + digest(raw);
    }

    /**
     * 构建原始积分流水缓存键
     */
    private String buildFlowsKey(long current, long size, String userUuid, String bizType) {
        String normalizedUserUuid = normalizeOptionalText(userUuid);
        String version = normalizedUserUuid == null ? getGlobalVersion() : getUserVersion(normalizedUserUuid);
        String raw = String.join("|", "flows", cachePart(normalizedUserUuid), version,
                String.valueOf(current), String.valueOf(size), cachePart(bizType));
        return KEY_PREFIX + "flows:" + digest(raw);
    }

    /**
     * 读取分页缓存
     */
    private <T> Page<T> readPage(String key, Class<T> recordClass) {
        try {
            String cached = redisService.get(key);
            if (StrUtil.isBlank(cached)) {
                return null;
            }
            JavaType pageType = objectMapper.getTypeFactory().constructParametricType(CachedPage.class, recordClass);
            CachedPage<T> cachedPage = objectMapper.readValue(cached, pageType);
            Page<T> page = new Page<>(cachedPage.getCurrent(), cachedPage.getSize(), cachedPage.getTotal());
            page.setRecords(cachedPage.getRecords() == null ? Collections.emptyList() : cachedPage.getRecords());
            log.debug("命中积分日志分页缓存: key={}, total={}, records={}", key, page.getTotal(), page.getRecords().size());
            return page;
        } catch (Exception e) {
            log.warn("读取积分日志分页缓存失败，已忽略缓存: key={}, reason={}", key, e.getMessage());
            safeDelete(key);
            return null;
        }
    }

    /**
     * 写入分页缓存
     */
    private <T> void writePage(String key, Page<T> page) {
        if (page == null) {
            return;
        }
        try {
            CachedPage<T> cachedPage = new CachedPage<>();
            cachedPage.setCurrent(page.getCurrent());
            cachedPage.setSize(page.getSize());
            cachedPage.setTotal(page.getTotal());
            cachedPage.setRecords(page.getRecords());
            redisService.set(key, objectMapper.writeValueAsString(cachedPage), randomTtl());
            log.debug("写入积分日志分页缓存: key={}, total={}, records={}", key, page.getTotal(), page.getRecords().size());
        } catch (Exception e) {
            log.warn("写入积分日志分页缓存失败: key={}, reason={}", key, e.getMessage());
        }
    }

    /**
     * 获取用户缓存版本
     */
    private String getUserVersion(String userUuid) {
        try {
            String version = redisService.get(USER_VERSION_PREFIX + requireUserUuid(userUuid));
            return StrUtil.blankToDefault(version, "0").trim();
        } catch (Exception e) {
            log.warn("读取用户积分日志缓存版本失败，使用默认版本: userUuid={}, reason={}", userUuid, e.getMessage());
            return "0";
        }
    }

    /**
     * 获取全局缓存版本
     */
    private String getGlobalVersion() {
        try {
            String version = redisService.get(GLOBAL_VERSION_KEY);
            return StrUtil.blankToDefault(version, "0").trim();
        } catch (Exception e) {
            log.warn("读取全局积分日志缓存版本失败，使用默认版本: reason={}", e.getMessage());
            return "0";
        }
    }

    /**
     * 安全删除异常缓存
     */
    private void safeDelete(String key) {
        try {
            redisService.delete(key);
        } catch (Exception ignored) {
            // 忽略删除失败，避免缓存故障影响主流程
        }
    }

    /**
     * 构建带随机抖动的缓存时间
     */
    private Duration randomTtl() {
        return Duration.ofSeconds(CACHE_BASE_TTL_SECONDS + ThreadLocalRandom.current().nextLong(0, CACHE_RANDOM_TTL_SECONDS));
    }

    /**
     * 生成缓存键摘要
     */
    private String digest(String raw) {
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 标准化可选缓存字段
     */
    private String cachePart(String value) {
        return StrUtil.blankToDefault(value, "_").trim();
    }

    /**
     * 标准化可选文本
     */
    private String normalizeOptionalText(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 校验用户UUID
     */
    private String requireUserUuid(String userUuid) {
        if (StrUtil.isBlank(userUuid)) {
            throw new IllegalArgumentException("userUuid不能为空");
        }
        return userUuid.trim();
    }

    /**
     * 缓存分页载体
     */
    private static class CachedPage<T> {
        private long current;
        private long size;
        private long total;
        private List<T> records;

        public long getCurrent() {
            return current;
        }

        public void setCurrent(long current) {
            this.current = current;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long total) {
            this.total = total;
        }

        public List<T> getRecords() {
            return records;
        }

        public void setRecords(List<T> records) {
            this.records = records;
        }
    }
}