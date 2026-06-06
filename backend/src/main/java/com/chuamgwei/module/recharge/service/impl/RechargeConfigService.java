package com.chuamgwei.module.recharge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.infrastructure.entity.SysAuditLog;
import com.chuamgwei.infrastructure.entity.SysConfig;
import com.chuamgwei.infrastructure.mapper.SysAuditLogMapper;
import com.chuamgwei.infrastructure.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 充值管理配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeConfigService {

    private static final int RATIO_SCALE = 6;
    private static final BigDecimal DEFAULT_CHARGE_RATIO = new BigDecimal("500000");
    private static final int DEFAULT_PAY_TIMEOUT_MINUTES = 30;
    private static final int MIN_PAY_TIMEOUT_MINUTES = 1;
    private static final int MAX_PAY_TIMEOUT_MINUTES = 1440;
    private static final String DEFAULT_PAY_SUBJECT_PREFIX = "创维AI充值";
    private static final String CONFIG_KEY_CHARGE_RATIO = "charge_ratio";
    private static final String CONFIG_KEY_PAY_TIMEOUT_MINUTES = "recharge_pay_timeout_minutes";
    private static final String CONFIG_KEY_PAY_SUBJECT_PREFIX = "recharge_pay_subject_prefix";

    private final SysConfigMapper sysConfigMapper;
    private final SysAuditLogMapper sysAuditLogMapper;

    /**
     * 获取系统当前的充值兑换比例
     */
    public BigDecimal getChargeRatio() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CONFIG_KEY_CHARGE_RATIO)
        );
        if (config == null) {
            log.warn("系统配置中未找到兑换比例 charge_ratio，采用默认值 {}", DEFAULT_CHARGE_RATIO);
            return normalizeRatio(DEFAULT_CHARGE_RATIO);
        }
        try {
            return normalizeRatio(new BigDecimal(config.getConfigValue()));
        } catch (NumberFormatException e) {
            log.error("兑换比例配置格式错误: {}", config.getConfigValue());
            return normalizeRatio(DEFAULT_CHARGE_RATIO);
        }
    }

    /**
     * 获取充值管理配置视图
     */
    public Map<String, Object> getRechargeSettings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("ratio", getChargeRatio());
        settings.put("payTimeoutMinutes", getPayTimeoutMinutes());
        settings.put("paySubjectPrefix", getPaySubjectPrefix());
        return settings;
    }

    /**
     * 获取支付超时时间
     */
    public Integer getPayTimeoutMinutes() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CONFIG_KEY_PAY_TIMEOUT_MINUTES)
        );
        if (config == null) {
            return DEFAULT_PAY_TIMEOUT_MINUTES;
        }
        try {
            return normalizePayTimeoutMinutes(Integer.parseInt(config.getConfigValue()));
        } catch (RuntimeException e) {
            log.error("支付超时时间配置格式错误: {}", config.getConfigValue());
            return DEFAULT_PAY_TIMEOUT_MINUTES;
        }
    }

    /**
     * 获取支付宝支付名称前缀
     */
    public String getPaySubjectPrefix() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, CONFIG_KEY_PAY_SUBJECT_PREFIX)
        );
        if (config == null || config.getConfigValue() == null || config.getConfigValue().trim().isEmpty()) {
            return DEFAULT_PAY_SUBJECT_PREFIX;
        }
        return config.getConfigValue().trim();
    }

    /**
     * 修改充值兑换比例
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChargeRatio(BigDecimal ratio, String operatorUuid, String operatorName) {
        BigDecimal normalizedRatio = normalizeRatio(ratio);
        saveConfig(CONFIG_KEY_CHARGE_RATIO, formatDecimalForStorage(normalizedRatio), "充值兑换比例");
        recordAudit(operatorUuid, operatorName, "UPDATE_CHARGE_RATIO", "新比例:" + formatDecimalForStorage(normalizedRatio));
        log.info("充值比例已更新为: {}", normalizedRatio);
        return true;
    }

    /**
     * 修改充值管理配置
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean updateRechargeSettings(BigDecimal ratio, Integer payTimeoutMinutes, String paySubjectPrefix,
                                          String operatorUuid, String operatorName) {
        BigDecimal normalizedRatio = normalizeRatio(ratio);
        Integer normalizedTimeout = normalizePayTimeoutMinutes(payTimeoutMinutes);
        String normalizedSubjectPrefix = normalizePaySubjectPrefix(paySubjectPrefix);

        saveConfig(CONFIG_KEY_CHARGE_RATIO, formatDecimalForStorage(normalizedRatio), "充值兑换比例");
        saveConfig(CONFIG_KEY_PAY_TIMEOUT_MINUTES, normalizedTimeout.toString(), "充值支付超时时间（分钟）");
        saveConfig(CONFIG_KEY_PAY_SUBJECT_PREFIX, normalizedSubjectPrefix, "支付宝支付名称");

        recordAudit(operatorUuid, operatorName, "UPDATE_RECHARGE_SETTINGS",
                "新比例:" + formatDecimalForStorage(normalizedRatio)
                        + ", 支付超时:" + normalizedTimeout + "分钟"
                        + ", 支付名称:" + normalizedSubjectPrefix);
        log.info("充值管理配置已更新: ratio={}, timeoutMinutes={}, subjectPrefix={}",
                normalizedRatio, normalizedTimeout, normalizedSubjectPrefix);
        return true;
    }

    /**
     * 标准化充值倍率
     */
    private BigDecimal normalizeRatio(BigDecimal ratio) {
        if (ratio == null || ratio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("充值兑换比例必须大于零");
        }
        return ratio.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 标准化支付超时时间
     */
    private Integer normalizePayTimeoutMinutes(Integer payTimeoutMinutes) {
        if (payTimeoutMinutes == null
                || payTimeoutMinutes < MIN_PAY_TIMEOUT_MINUTES
                || payTimeoutMinutes > MAX_PAY_TIMEOUT_MINUTES) {
            throw new RuntimeException("支付超时时间必须在1到1440分钟之间");
        }
        return payTimeoutMinutes;
    }

    /**
     * 标准化支付宝支付名称前缀
     */
    private String normalizePaySubjectPrefix(String paySubjectPrefix) {
        if (paySubjectPrefix == null || paySubjectPrefix.trim().isEmpty()) {
            throw new RuntimeException("支付宝支付名称不能为空");
        }
        String normalized = paySubjectPrefix.trim();
        if (normalized.length() > 80) {
            throw new RuntimeException("支付宝支付名称不能超过80个字符");
        }
        return normalized;
    }

    /**
     * 保存系统配置
     */
    private void saveConfig(String configKey, String configValue, String remark) {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, configKey)
        );
        if (config == null) {
            config = new SysConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setRemark(remark);
            config.setUpdateTime(System.currentTimeMillis() / 1000);
            sysConfigMapper.insert(config);
            return;
        }
        config.setConfigValue(configValue);
        config.setRemark(remark);
        config.setUpdateTime(System.currentTimeMillis() / 1000);
        sysConfigMapper.updateById(config);
    }

    /**
     * 记录充值配置审计日志
     */
    private void recordAudit(String operatorUuid, String operatorName, String action, String target) {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setOperatorUuid(operatorUuid);
        auditLog.setOperatorName(operatorName);
        auditLog.setAction(action);
        auditLog.setTarget(target);
        auditLog.setResult("SUCCESS");
        auditLog.setCreateTime(System.currentTimeMillis() / 1000);
        sysAuditLogMapper.insert(auditLog);
    }

    /**
     * 保存配置时去掉无意义尾零
     */
    private String formatDecimalForStorage(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}