package com.chuamgwei.module.credit.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.infrastructure.entity.SysAuditLog;
import com.chuamgwei.infrastructure.mapper.SysAuditLogMapper;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditAccountVO;
import com.chuamgwei.module.credit.entity.CreditBillingResult;
import com.chuamgwei.module.credit.entity.CreditConsumeRecord;
import com.chuamgwei.module.credit.entity.CreditFlow;
import com.chuamgwei.module.credit.entity.CreditLogVO;
import com.chuamgwei.module.credit.mapper.CreditAccountMapper;
import com.chuamgwei.module.credit.mapper.CreditConsumeRecordMapper;
import com.chuamgwei.module.credit.mapper.CreditFlowMapper;
import com.chuamgwei.module.credit.mapper.CreditLogMapper;
import com.chuamgwei.module.credit.service.CreditService;
import com.chuamgwei.module.redis.service.CreditBalanceCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 积分账户业务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CreditServiceImpl implements CreditService {

    private static final int POINT_SCALE = 6;
    private static final int MAX_BIZ_TYPE_LENGTH = 30;
    private static final int CLIENT_CONSUME_PAGE_SIZE = 20;
    private static final int CLIENT_LOG_MAX_PAGE_SIZE = 100;
    private static final String FLOW_PREFIX = "FLW";
    private static final String OPERATOR_SYSTEM = "system";
    private static final String OPERATOR_NEW_API = "new-api";
    private static final String STAGE_PRE = "PRE";
    private static final String STAGE_SETTLE = "SETTLE";
    private static final String STAGE_REFUND = "REFUND";
    private static final String STATUS_PRE_CONSUMED = "PRE_CONSUMED";
    private static final String STATUS_SETTLED = "SETTLED";
    private static final String STATUS_REFUNDED = "REFUNDED";
    private static final String STATUS_TEXT_PRE_CONSUMED = "结算中";
    private static final String STATUS_TEXT_SETTLED = "已结算";
    private static final String STATUS_TEXT_REFUNDED = "已退款";
    private static final String CONSUME_RECORD_TITLE_NEW_API_CHAT = "AI 对话消费";
    private static final BigDecimal ZERO_POINTS = BigDecimal.ZERO.setScale(POINT_SCALE, RoundingMode.HALF_UP);

    private final CreditAccountMapper creditAccountMapper;
    private final CreditFlowMapper creditFlowMapper;
    private final CreditConsumeRecordMapper creditConsumeRecordMapper;
    private final CreditLogMapper creditLogMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final CreditBalanceCacheService creditBalanceCacheService;

    @Override
    public CreditAccount getOrCreateAccount(String userUuid) {
        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, userUuid)
        );
        if (account == null) {
            account = new CreditAccount();
            account.setUserUuid(userUuid);
            account.setTotalPoints(ZERO_POINTS);
            account.setAvailablePoints(ZERO_POINTS);
            account.setFrozenPoints(ZERO_POINTS);
            account.setTotalRechargePoints(ZERO_POINTS);
            account.setTotalConsumePoints(ZERO_POINTS);
            account.setTotalRefundPoints(ZERO_POINTS);
            account.setStatus(1);
            account.setVersion(0);
            creditAccountMapper.insert(account);
        }
        return account;
    }

    @Override
    public CreditAccount getBalance(String userUuid) {
        // 先查 Redis 缓存
        CreditAccount cachedAccount = creditBalanceCacheService.getCachedAccount(userUuid);
        if (cachedAccount != null) {
            log.debug("命中积分账户缓存: userUuid={}, availablePoints={}", userUuid, cachedAccount.getAvailablePoints());
            return cachedAccount;
        }

        // 未命中，查 MySQL
        CreditAccount account = getOrCreateAccount(userUuid);
        
        // 写入 Redis 缓存
        creditBalanceCacheService.cacheAccount(userUuid, account);
        
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditBillingResult preConsume(String userUuid, String bizType, String bizOrderNo,
                                          BigDecimal estimatedPoints, String remark) {
        String normalizedBizType = normalizeBizType(bizType);
        String normalizedBizOrderNo = normalizeBizOrderNo(bizOrderNo);
        BigDecimal normalizedPoints = normalizePositivePoints(estimatedPoints);

        CreditBillingResult existingResult = buildExistingLifecycleResult(userUuid, normalizedBizType, normalizedBizOrderNo,
                normalizedPoints, null);
        if (existingResult != null) {
            return existingResult;
        }

        CreditAccount account = lockAccount(userUuid);
        existingResult = buildExistingLifecycleResult(userUuid, normalizedBizType, normalizedBizOrderNo,
                normalizedPoints, null);
        if (existingResult != null) {
            return existingResult;
        }

        checkAccountAvailable(account);
        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        BigDecimal afterAvailable = beforeAvailable.subtract(normalizedPoints);
        BigDecimal afterFrozen = beforeFrozen.add(normalizedPoints);

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .eq(CreditAccount::getVersion, account.getVersion())
                        .eq(CreditAccount::getStatus, 1)
                        .ge(CreditAccount::getAvailablePoints, normalizedPoints)
        );
        if (rows == 0) {
            throw new RuntimeException("积分余额不足，无法完成预扣");
        }

        account.setAvailablePoints(afterAvailable);
        account.setFrozenPoints(afterFrozen);
        account.setVersion(account.getVersion() + 1);

        insertFlow(account, beforeAvailable, normalizedPoints.negate(), afterAvailable,
                beforeFrozen, normalizedPoints, afterFrozen,
                stageBizType(normalizedBizType, STAGE_PRE), normalizedBizOrderNo,
                OPERATOR_NEW_API, defaultRemark(remark, "new-api 请求预扣积分"));
        upsertPreConsumedRecord(account, normalizedBizType, normalizedBizOrderNo, normalizedPoints,
                beforeAvailable, afterAvailable, defaultRemark(remark, "new-api 请求预扣积分"));

        // 余额变动，删除缓存
        creditBalanceCacheService.evictBalance(userUuid);

        log.info("内部预扣积分成功: userUuid={}, bizType={}, bizOrderNo={}, points={}",
                userUuid, normalizedBizType, normalizedBizOrderNo, normalizedPoints);
        return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_PRE_CONSUMED,
                normalizedPoints, normalizedPoints, null, account, false, "预扣成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditBillingResult settle(String userUuid, String bizType, String bizOrderNo,
                                      BigDecimal actualPoints, String remark) {
        String normalizedBizType = normalizeBizType(bizType);
        String normalizedBizOrderNo = normalizeBizOrderNo(bizOrderNo);
        BigDecimal normalizedActualPoints = normalizeNonNegativePoints(actualPoints);

        CreditFlow existingSettleFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_SETTLE);
        if (existingSettleFlow != null) {
            CreditAccount account = getOrCreateAccount(userUuid);
            return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_SETTLED,
                    normalizedActualPoints, findPreConsumedPoints(normalizedBizType, normalizedBizOrderNo),
                    normalizedActualPoints, account, true, "结算已处理");
        }
        CreditFlow existingRefundFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_REFUND);
        if (existingRefundFlow != null) {
            throw new RuntimeException("该业务单已退款，不能再次结算");
        }

        CreditAccount account = lockAccount(userUuid);
        existingSettleFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_SETTLE);
        if (existingSettleFlow != null) {
            return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_SETTLED,
                    normalizedActualPoints, findPreConsumedPoints(normalizedBizType, normalizedBizOrderNo),
                    normalizedActualPoints, account, true, "结算已处理");
        }
        existingRefundFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_REFUND);
        if (existingRefundFlow != null) {
            throw new RuntimeException("该业务单已退款，不能再次结算");
        }

        CreditFlow preFlow = requirePreConsumeFlow(normalizedBizType, normalizedBizOrderNo);
        BigDecimal preConsumedPoints = normalizeNonNegativePoints(preFlow.getChangeFrozenPoints());
        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        if (beforeFrozen.compareTo(preConsumedPoints) < 0) {
            throw new RuntimeException("冻结积分不足，无法完成结算");
        }

        BigDecimal releasePoints = preConsumedPoints.subtract(normalizedActualPoints);
        BigDecimal extraConsumePoints = normalizedActualPoints.subtract(preConsumedPoints);
        if (releasePoints.compareTo(BigDecimal.ZERO) < 0) {
            releasePoints = ZERO_POINTS;
        }
        if (extraConsumePoints.compareTo(BigDecimal.ZERO) < 0) {
            extraConsumePoints = ZERO_POINTS;
        }
        if (extraConsumePoints.compareTo(BigDecimal.ZERO) > 0 && beforeAvailable.compareTo(extraConsumePoints) < 0) {
            throw new RuntimeException("可用积分不足，无法完成补扣结算");
        }

        BigDecimal availableChange = releasePoints.subtract(extraConsumePoints).setScale(POINT_SCALE, RoundingMode.HALF_UP);
        BigDecimal frozenChange = preConsumedPoints.negate();
        BigDecimal afterAvailable = beforeAvailable.add(availableChange);
        BigDecimal afterFrozen = beforeFrozen.subtract(preConsumedPoints);
        BigDecimal afterConsume = valueOrZero(account.getTotalConsumePoints()).add(normalizedActualPoints);
        BigDecimal afterRefund = valueOrZero(account.getTotalRefundPoints()).add(releasePoints);

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getTotalConsumePoints, afterConsume)
                        .set(CreditAccount::getTotalRefundPoints, afterRefund)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .eq(CreditAccount::getVersion, account.getVersion())
                        .eq(CreditAccount::getStatus, 1)
                        .ge(CreditAccount::getFrozenPoints, preConsumedPoints)
                        .ge(extraConsumePoints.compareTo(BigDecimal.ZERO) > 0,
                                CreditAccount::getAvailablePoints, extraConsumePoints)
        );
        if (rows == 0) {
            throw new RuntimeException("积分结算失败，并发冲突");
        }

        account.setAvailablePoints(afterAvailable);
        account.setFrozenPoints(afterFrozen);
        account.setTotalConsumePoints(afterConsume);
        account.setTotalRefundPoints(afterRefund);
        account.setVersion(account.getVersion() + 1);

        insertFlow(account, beforeAvailable, availableChange, afterAvailable,
                beforeFrozen, frozenChange, afterFrozen,
                stageBizType(normalizedBizType, STAGE_SETTLE), normalizedBizOrderNo,
                OPERATOR_NEW_API, defaultRemark(remark, "new-api 请求完成，按实际用量结算"));
        upsertSettledRecord(account, normalizedBizType, normalizedBizOrderNo, preFlow,
                preConsumedPoints, normalizedActualPoints, releasePoints, extraConsumePoints,
                afterAvailable, defaultRemark(remark, "new-api 请求完成，按实际用量结算"));

        // 余额变动，删除缓存
        creditBalanceCacheService.evictBalance(userUuid);

        log.info("内部结算积分成功: userUuid={}, bizType={}, bizOrderNo={}, pre={}, actual={}",
                userUuid, normalizedBizType, normalizedBizOrderNo, preConsumedPoints, normalizedActualPoints);
        return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_SETTLED,
                normalizedActualPoints, preConsumedPoints, normalizedActualPoints, account, false, "结算成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditBillingResult refund(String userUuid, String bizType, String bizOrderNo, String remark) {
        String normalizedBizType = normalizeBizType(bizType);
        String normalizedBizOrderNo = normalizeBizOrderNo(bizOrderNo);

        CreditFlow existingRefundFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_REFUND);
        if (existingRefundFlow != null) {
            CreditAccount account = getOrCreateAccount(userUuid);
            return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_REFUNDED,
                    null, findPreConsumedPoints(normalizedBizType, normalizedBizOrderNo), null,
                    account, true, "退款已处理");
        }
        CreditFlow existingSettleFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_SETTLE);
        if (existingSettleFlow != null) {
            throw new RuntimeException("该业务单已结算，不能再次退款");
        }

        CreditAccount account = lockAccount(userUuid);
        existingRefundFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_REFUND);
        if (existingRefundFlow != null) {
            return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_REFUNDED,
                    null, findPreConsumedPoints(normalizedBizType, normalizedBizOrderNo), null,
                    account, true, "退款已处理");
        }
        existingSettleFlow = selectStageFlow(normalizedBizType, normalizedBizOrderNo, STAGE_SETTLE);
        if (existingSettleFlow != null) {
            throw new RuntimeException("该业务单已结算，不能再次退款");
        }

        CreditFlow preFlow = requirePreConsumeFlow(normalizedBizType, normalizedBizOrderNo);
        BigDecimal refundPoints = normalizeNonNegativePoints(preFlow.getChangeFrozenPoints());
        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        if (beforeFrozen.compareTo(refundPoints) < 0) {
            throw new RuntimeException("冻结积分不足，无法完成退款");
        }

        BigDecimal afterAvailable = beforeAvailable.add(refundPoints);
        BigDecimal afterFrozen = beforeFrozen.subtract(refundPoints);
        BigDecimal afterRefund = valueOrZero(account.getTotalRefundPoints()).add(refundPoints);

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getTotalRefundPoints, afterRefund)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .eq(CreditAccount::getVersion, account.getVersion())
                        .eq(CreditAccount::getStatus, 1)
                        .ge(CreditAccount::getFrozenPoints, refundPoints)
        );
        if (rows == 0) {
            throw new RuntimeException("积分退款失败，并发冲突");
        }

        account.setAvailablePoints(afterAvailable);
        account.setFrozenPoints(afterFrozen);
        account.setTotalRefundPoints(afterRefund);
        account.setVersion(account.getVersion() + 1);

        insertFlow(account, beforeAvailable, refundPoints, afterAvailable,
                beforeFrozen, refundPoints.negate(), afterFrozen,
                stageBizType(normalizedBizType, STAGE_REFUND), normalizedBizOrderNo,
                OPERATOR_NEW_API, defaultRemark(remark, "new-api 请求失败，退回预扣积分"));
        upsertRefundedRecord(account, normalizedBizType, normalizedBizOrderNo, preFlow,
                refundPoints, afterAvailable, defaultRemark(remark, "new-api 请求失败，退回预扣积分"));

        // 余额变动，删除缓存
        creditBalanceCacheService.evictBalance(userUuid);

        log.info("内部退款积分成功: userUuid={}, bizType={}, bizOrderNo={}, points={}",
                userUuid, normalizedBizType, normalizedBizOrderNo, refundPoints);
        return buildResult(userUuid, normalizedBizType, normalizedBizOrderNo, STATUS_REFUNDED,
                null, refundPoints, null, account, false, "退款成功");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreditAccount addPoints(String userUuid, BigDecimal points, String bizOrderNo, String remark) {
        BigDecimal normalizedPoints = normalizePositivePoints(points);
        CreditAccount account = getOrCreateAccount(userUuid);

        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeTotal = valueOrZero(account.getTotalPoints());
        BigDecimal beforeRecharge = valueOrZero(account.getTotalRechargePoints());
        BigDecimal frozenPoints = valueOrZero(account.getFrozenPoints());
        BigDecimal afterAvailable = beforeAvailable.add(normalizedPoints);
        BigDecimal afterTotal = beforeTotal.add(normalizedPoints);
        BigDecimal afterRecharge = beforeRecharge.add(normalizedPoints);

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getTotalPoints, afterTotal)
                        .set(CreditAccount::getTotalRechargePoints, afterRecharge)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .eq(CreditAccount::getVersion, account.getVersion())
        );

        if (rows == 0) {
            throw new RuntimeException("积分入账失败，并发冲突");
        }

        CreditFlow flow = new CreditFlow();
        flow.setFlowNo(FLOW_PREFIX + IdUtil.getSnowflakeNextIdStr());
        flow.setUserUuid(userUuid);
        flow.setAccountId(account.getId());
        flow.setBeforeAvailablePoints(beforeAvailable);
        flow.setChangeAvailablePoints(normalizedPoints);
        flow.setAfterAvailablePoints(afterAvailable);
        flow.setBeforeFrozenPoints(frozenPoints);
        flow.setChangeFrozenPoints(ZERO_POINTS);
        flow.setAfterFrozenPoints(frozenPoints);
        flow.setBizType("RECHARGE");
        flow.setBizOrderNo(bizOrderNo);
        flow.setOperatorName(OPERATOR_SYSTEM);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(flow);

        log.info("积分入账成功: 用户={}, 积分={}, 变动前={}, 变动后={}",
                userUuid, normalizedPoints, beforeAvailable, afterAvailable);

        account.setAvailablePoints(afterAvailable);
        account.setTotalPoints(afterTotal);
        account.setTotalRechargePoints(afterRecharge);
        
        // 余额变动，删除缓存
        creditBalanceCacheService.evictBalance(userUuid);
        
        return account;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adjustBalance(String userUuid, Integer adjustType, BigDecimal points, String reason,
                                  String operatorUuid, String operatorName) {
        BigDecimal normalizedPoints = normalizePositivePoints(points);
        log.info("调账: 操作人={}, 目标用户={}, 方向={}, 积分={}, 原因={}",
                operatorName, userUuid, adjustType == 1 ? "加" : "扣", normalizedPoints, reason);

        CreditAccount account = getOrCreateAccount(userUuid);

        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeTotal = valueOrZero(account.getTotalPoints());
        BigDecimal frozenPoints = valueOrZero(account.getFrozenPoints());
        BigDecimal changePoints = adjustType == 1 ? normalizedPoints : normalizedPoints.negate();
        BigDecimal afterAvailable = beforeAvailable.add(changePoints);
        BigDecimal afterTotal = beforeTotal.add(changePoints);

        if (afterAvailable.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("扣减后可用积分不能为负数");
        }

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getTotalPoints, afterTotal)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .eq(CreditAccount::getVersion, account.getVersion())
        );

        if (rows == 0) {
            throw new RuntimeException("调账失败，并发冲突");
        }

        CreditFlow flow = new CreditFlow();
        flow.setFlowNo(FLOW_PREFIX + IdUtil.getSnowflakeNextIdStr());
        flow.setUserUuid(userUuid);
        flow.setAccountId(account.getId());
        flow.setBeforeAvailablePoints(beforeAvailable);
        flow.setChangeAvailablePoints(changePoints);
        flow.setAfterAvailablePoints(afterAvailable);
        flow.setBeforeFrozenPoints(frozenPoints);
        flow.setChangeFrozenPoints(ZERO_POINTS);
        flow.setAfterFrozenPoints(frozenPoints);
        flow.setBizType(adjustType == 1 ? "ADMIN_ADD" : "ADMIN_DEDUCT");
        flow.setBizOrderNo("ADJ_" + IdUtil.getSnowflakeNextIdStr());
        flow.setOperatorUuid(operatorUuid);
        flow.setOperatorName(operatorName);
        flow.setRemark(reason);
        flow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(flow);

        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setOperatorUuid(operatorUuid);
        auditLog.setOperatorName(operatorName);
        auditLog.setAction(adjustType == 1 ? "ADMIN_ADD" : "ADMIN_DEDUCT");
        auditLog.setTarget("用户:" + userUuid + ", 积分:" + normalizedPoints);
        auditLog.setResult("SUCCESS");
        auditLog.setCreateTime(System.currentTimeMillis() / 1000);
        sysAuditLogMapper.insert(auditLog);

        // 余额变动，删除缓存
        creditBalanceCacheService.evictBalance(userUuid);

        log.info("调账完成: 用户={}, 变动前={}, 变动={}, 变动后={}",
                userUuid, beforeAvailable, changePoints, afterAvailable);
        return true;
    }

    @Override
    public Page<CreditAccountVO> pageAccounts(Integer current, Integer size, String keyword) {
        Page<CreditAccountVO> page = new Page<>(current, size);
        if (StrUtil.isBlank(keyword)) {
            return creditAccountMapper.selectAccountPage(page);
        }
        return creditAccountMapper.searchAccountPage(page, keyword);
    }

    @Override
    public Page<CreditConsumeRecord> pageConsumeRecords(Integer current, Integer size, String userUuid) {
        validateUserUuid(userUuid);
        Page<CreditConsumeRecord> page = new Page<>(normalizeCurrent(current), CLIENT_CONSUME_PAGE_SIZE);
        return creditConsumeRecordMapper.selectConsumeRecordPage(page, userUuid);
    }

    @Override
    public Page<CreditLogVO> pageUserLogs(Integer current, Integer size, String userUuid, String type,
                                          String direction, String keyword, String startTime, String endTime) {
        validateUserUuid(userUuid);
        Page<CreditLogVO> page = new Page<>(normalizeCurrent(current), normalizeLogPageSize(size));
        return creditLogMapper.selectUserLogPage(
                page,
                userUuid,
                normalizeCreditLogType(type),
                normalizeCreditLogDirection(direction),
                normalizeOptionalText(keyword),
                normalizeOptionalText(startTime),
                normalizeOptionalText(endTime)
        );
    }

    @Override
    public Page<CreditFlow> pageFlows(Integer current, Integer size, String userUuid, String bizType) {
        Page<CreditFlow> page = new Page<>(current, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CreditFlow> wrapper =
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(StrUtil.isNotBlank(userUuid), CreditFlow::getUserUuid, userUuid)
                        .eq(StrUtil.isNotBlank(bizType), CreditFlow::getBizType, bizType)
                        .orderByDesc(CreditFlow::getCreatedAt);
        return creditFlowMapper.selectPage(page, wrapper);
    }

    /**
     * 锁定积分账户
     */
    private CreditAccount lockAccount(String userUuid) {
        validateUserUuid(userUuid);
        getOrCreateAccount(userUuid);
        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery()
                        .eq(CreditAccount::getUserUuid, userUuid)
                        .last("for update")
        );
        if (account == null) {
            throw new RuntimeException("积分账户不存在");
        }
        return account;
    }

    /**
     * 生成阶段业务类型
     */
    private String stageBizType(String bizType, String stage) {
        String stageBizType = bizType + "_" + stage;
        if (stageBizType.length() > MAX_BIZ_TYPE_LENGTH) {
            throw new IllegalArgumentException("业务类型过长，无法生成阶段流水类型");
        }
        return stageBizType;
    }

    /**
     * 查询阶段流水
     */
    private CreditFlow selectStageFlow(String bizType, String bizOrderNo, String stage) {
        return creditFlowMapper.selectOne(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getBizType, stageBizType(bizType, stage))
                        .eq(CreditFlow::getBizOrderNo, bizOrderNo)
        );
    }

    /**
     * 必须存在预扣流水
     */
    private CreditFlow requirePreConsumeFlow(String bizType, String bizOrderNo) {
        CreditFlow preFlow = selectStageFlow(bizType, bizOrderNo, STAGE_PRE);
        if (preFlow == null) {
            throw new RuntimeException("未找到预扣记录，无法继续处理");
        }
        return preFlow;
    }

    /**
     * 查找预扣积分
     */
    private BigDecimal findPreConsumedPoints(String bizType, String bizOrderNo) {
        CreditFlow preFlow = selectStageFlow(bizType, bizOrderNo, STAGE_PRE);
        if (preFlow == null) {
            return ZERO_POINTS;
        }
        return valueOrZero(preFlow.getChangeFrozenPoints());
    }

    /**
     * 构造已存在生命周期的幂等结果
     */
    private CreditBillingResult buildExistingLifecycleResult(String userUuid, String bizType, String bizOrderNo,
                                                             BigDecimal requestedPoints, BigDecimal actualPoints) {
        CreditFlow refundFlow = selectStageFlow(bizType, bizOrderNo, STAGE_REFUND);
        if (refundFlow != null) {
            CreditAccount account = getOrCreateAccount(userUuid);
            return buildResult(userUuid, bizType, bizOrderNo, STATUS_REFUNDED, requestedPoints,
                    findPreConsumedPoints(bizType, bizOrderNo), actualPoints, account, true, "退款已处理");
        }
        CreditFlow settleFlow = selectStageFlow(bizType, bizOrderNo, STAGE_SETTLE);
        if (settleFlow != null) {
            CreditAccount account = getOrCreateAccount(userUuid);
            return buildResult(userUuid, bizType, bizOrderNo, STATUS_SETTLED, requestedPoints,
                    findPreConsumedPoints(bizType, bizOrderNo), actualPoints, account, true, "结算已处理");
        }
        CreditFlow preFlow = selectStageFlow(bizType, bizOrderNo, STAGE_PRE);
        if (preFlow != null) {
            CreditAccount account = getOrCreateAccount(userUuid);
            return buildResult(userUuid, bizType, bizOrderNo, STATUS_PRE_CONSUMED, requestedPoints,
                    valueOrZero(preFlow.getChangeFrozenPoints()), actualPoints, account, true, "预扣已处理");
        }
        return null;
    }

    /**
     * 写入预扣阶段消费记录
     */
    private void upsertPreConsumedRecord(CreditAccount account, String bizType, String bizOrderNo,
                                         BigDecimal preDeductPoints, BigDecimal balanceBefore,
                                         BigDecimal balanceAfter, String remark) {
        LocalDateTime now = LocalDateTime.now();
        CreditConsumeRecord record = newConsumeRecord(account.getUserUuid(), bizType, bizOrderNo, remark);
        record.setStatus(STATUS_PRE_CONSUMED);
        record.setStatusText(STATUS_TEXT_PRE_CONSUMED);
        record.setPreDeductPoints(valueOrZero(preDeductPoints));
        record.setActualCostPoints(null);
        record.setRefundPoints(ZERO_POINTS);
        record.setExtraDeductPoints(ZERO_POINTS);
        record.setFrozenPoints(valueOrZero(preDeductPoints));
        record.setBalanceBefore(valueOrZero(balanceBefore));
        record.setBalanceAfter(valueOrZero(balanceAfter));
        record.setStartedAt(now);
        record.setFinishedAt(null);
        record.setLatestAt(now);
        upsertConsumeRecord(record);
    }

    /**
     * 写入结算阶段消费记录
     */
    private void upsertSettledRecord(CreditAccount account, String bizType, String bizOrderNo, CreditFlow preFlow,
                                     BigDecimal preDeductPoints, BigDecimal actualCostPoints,
                                     BigDecimal refundPoints, BigDecimal extraDeductPoints,
                                     BigDecimal balanceAfter, String remark) {
        LocalDateTime now = LocalDateTime.now();
        CreditConsumeRecord record = newConsumeRecord(account.getUserUuid(), bizType, bizOrderNo, remark);
        record.setStatus(STATUS_SETTLED);
        record.setStatusText(STATUS_TEXT_SETTLED);
        record.setPreDeductPoints(valueOrZero(preDeductPoints));
        record.setActualCostPoints(valueOrZero(actualCostPoints));
        record.setRefundPoints(valueOrZero(refundPoints));
        record.setExtraDeductPoints(valueOrZero(extraDeductPoints));
        record.setFrozenPoints(ZERO_POINTS);
        record.setBalanceBefore(valueOrZero(preFlow.getBeforeAvailablePoints()));
        record.setBalanceAfter(valueOrZero(balanceAfter));
        record.setStartedAt(preFlow.getCreatedAt() == null ? now : preFlow.getCreatedAt());
        record.setFinishedAt(now);
        record.setLatestAt(now);
        upsertConsumeRecord(record);
    }

    /**
     * 写入退款阶段消费记录
     */
    private void upsertRefundedRecord(CreditAccount account, String bizType, String bizOrderNo, CreditFlow preFlow,
                                      BigDecimal refundPoints, BigDecimal balanceAfter, String remark) {
        LocalDateTime now = LocalDateTime.now();
        CreditConsumeRecord record = newConsumeRecord(account.getUserUuid(), bizType, bizOrderNo, remark);
        record.setStatus(STATUS_REFUNDED);
        record.setStatusText(STATUS_TEXT_REFUNDED);
        record.setPreDeductPoints(valueOrZero(refundPoints));
        record.setActualCostPoints(ZERO_POINTS);
        record.setRefundPoints(valueOrZero(refundPoints));
        record.setExtraDeductPoints(ZERO_POINTS);
        record.setFrozenPoints(ZERO_POINTS);
        record.setBalanceBefore(valueOrZero(preFlow.getBeforeAvailablePoints()));
        record.setBalanceAfter(valueOrZero(balanceAfter));
        record.setStartedAt(preFlow.getCreatedAt() == null ? now : preFlow.getCreatedAt());
        record.setFinishedAt(now);
        record.setLatestAt(now);
        upsertConsumeRecord(record);
    }

    /**
     * 创建消费记录基础数据
     */
    private CreditConsumeRecord newConsumeRecord(String userUuid, String bizType, String bizOrderNo, String remark) {
        CreditConsumeRecord record = new CreditConsumeRecord();
        record.setUserUuid(userUuid);
        record.setBizType(bizType);
        record.setBizOrderNo(bizOrderNo);
        record.setTitle(consumeRecordTitle(bizType));
        record.setRemark(remark);
        return record;
    }

    /**
     * 幂等写入消费记录读模型
     */
    private void upsertConsumeRecord(CreditConsumeRecord record) {
        int rows = updateConsumeRecord(record);
        if (rows > 0) {
            return;
        }
        try {
            creditConsumeRecordMapper.insert(record);
        } catch (DuplicateKeyException ignored) {
            updateConsumeRecord(record);
        }
    }

    /**
     * 更新消费记录读模型
     */
    private int updateConsumeRecord(CreditConsumeRecord record) {
        return creditConsumeRecordMapper.update(null,
                Wrappers.<CreditConsumeRecord>lambdaUpdate()
                        .set(CreditConsumeRecord::getBizType, record.getBizType())
                        .set(CreditConsumeRecord::getTitle, record.getTitle())
                        .set(CreditConsumeRecord::getStatus, record.getStatus())
                        .set(CreditConsumeRecord::getStatusText, record.getStatusText())
                        .set(CreditConsumeRecord::getPreDeductPoints, record.getPreDeductPoints())
                        .set(CreditConsumeRecord::getActualCostPoints, record.getActualCostPoints())
                        .set(CreditConsumeRecord::getRefundPoints, record.getRefundPoints())
                        .set(CreditConsumeRecord::getExtraDeductPoints, record.getExtraDeductPoints())
                        .set(CreditConsumeRecord::getFrozenPoints, record.getFrozenPoints())
                        .set(CreditConsumeRecord::getBalanceBefore, record.getBalanceBefore())
                        .set(CreditConsumeRecord::getBalanceAfter, record.getBalanceAfter())
                        .set(CreditConsumeRecord::getStartedAt, record.getStartedAt())
                        .set(CreditConsumeRecord::getFinishedAt, record.getFinishedAt())
                        .set(CreditConsumeRecord::getLatestAt, record.getLatestAt())
                        .set(CreditConsumeRecord::getRemark, record.getRemark())
                        .eq(CreditConsumeRecord::getUserUuid, record.getUserUuid())
                        .eq(CreditConsumeRecord::getBizOrderNo, record.getBizOrderNo())
        );
    }

    /**
     * 生成消费记录展示标题
     */
    private String consumeRecordTitle(String bizType) {
        if ("NEW_API_CHAT".equals(bizType)) {
            return CONSUME_RECORD_TITLE_NEW_API_CHAT;
        }
        return bizType + " 消费";
    }

    /**
     * 写入积分流水
     */
    private void insertFlow(CreditAccount account,
                            BigDecimal beforeAvailable, BigDecimal changeAvailable, BigDecimal afterAvailable,
                            BigDecimal beforeFrozen, BigDecimal changeFrozen, BigDecimal afterFrozen,
                            String bizType, String bizOrderNo, String operatorName, String remark) {
        CreditFlow flow = new CreditFlow();
        flow.setFlowNo(FLOW_PREFIX + IdUtil.getSnowflakeNextIdStr());
        flow.setUserUuid(account.getUserUuid());
        flow.setAccountId(account.getId());
        flow.setBeforeAvailablePoints(beforeAvailable);
        flow.setChangeAvailablePoints(changeAvailable);
        flow.setAfterAvailablePoints(afterAvailable);
        flow.setBeforeFrozenPoints(beforeFrozen);
        flow.setChangeFrozenPoints(changeFrozen);
        flow.setAfterFrozenPoints(afterFrozen);
        flow.setBizType(bizType);
        flow.setBizOrderNo(bizOrderNo);
        flow.setOperatorName(operatorName);
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(flow);
    }

    /**
     * 构造计费结果
     */
    private CreditBillingResult buildResult(String userUuid, String bizType, String bizOrderNo, String status,
                                            BigDecimal requestedPoints, BigDecimal preConsumedPoints,
                                            BigDecimal actualPoints, CreditAccount account,
                                            boolean idempotent, String message) {
        CreditBillingResult result = new CreditBillingResult();
        result.setUserUuid(userUuid);
        result.setBizType(bizType);
        result.setBizOrderNo(bizOrderNo);
        result.setStatus(status);
        result.setRequestedPoints(requestedPoints);
        result.setPreConsumedPoints(preConsumedPoints);
        result.setActualPoints(actualPoints);
        result.setAvailablePoints(valueOrZero(account.getAvailablePoints()));
        result.setFrozenPoints(valueOrZero(account.getFrozenPoints()));
        result.setIdempotent(idempotent);
        result.setMessage(message);
        return result;
    }

    /**
     * 校验账户状态
     */
    private void checkAccountAvailable(CreditAccount account) {
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new RuntimeException("积分账户不可用");
        }
    }

    /**
     * 标准化正向积分数量
     */
    private BigDecimal normalizePositivePoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("积分数量必须大于零");
        }
        return points.setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 标准化非负积分数量
     */
    private BigDecimal normalizeNonNegativePoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("积分数量不能为负数");
        }
        return points.setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 标准化业务类型
     */
    private String normalizeBizType(String bizType) {
        if (StrUtil.isBlank(bizType)) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        String normalized = bizType.trim().toUpperCase();
        if (normalized.contains(" ")) {
            throw new IllegalArgumentException("业务类型不能包含空格");
        }
        return normalized;
    }

    /**
     * 标准化业务单号
     */
    private String normalizeBizOrderNo(String bizOrderNo) {
        if (StrUtil.isBlank(bizOrderNo)) {
            throw new IllegalArgumentException("业务单号不能为空");
        }
        return bizOrderNo.trim();
    }

    /**
     * 标准化分页页码
     */
    private long normalizeCurrent(Integer current) {
        if (current == null || current < 1) {
            return 1L;
        }
        return current.longValue();
    }

    /**
     * 标准化用户端积分日志分页大小
     */
    private long normalizeLogPageSize(Integer size) {
        if (size == null || size < 1) {
            return CLIENT_CONSUME_PAGE_SIZE;
        }
        return Math.min(size.longValue(), CLIENT_LOG_MAX_PAGE_SIZE);
    }

    /**
     * 标准化用户端积分日志类型
     */
    private String normalizeCreditLogType(String type) {
        String normalized = normalizeOptionalUpperText(type);
        if (normalized == null) {
            return null;
        }
        if ("RECHARGE".equals(normalized) || "CONSUME".equals(normalized)
                || "REFUND".equals(normalized) || "ADJUST".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的积分日志类型");
    }

    /**
     * 标准化用户端积分日志方向
     */
    private String normalizeCreditLogDirection(String direction) {
        String normalized = normalizeOptionalUpperText(direction);
        if (normalized == null) {
            return null;
        }
        if ("IN".equals(normalized) || "OUT".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("不支持的积分日志方向");
    }

    /**
     * 标准化可选文本
     */
    private String normalizeOptionalText(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    /**
     * 标准化可选大写文本
     */
    private String normalizeOptionalUpperText(String value) {
        String normalized = normalizeOptionalText(value);
        return normalized == null ? null : normalized.toUpperCase();
    }

    /**
     * 校验用户UUID
     */
    private void validateUserUuid(String userUuid) {
        if (StrUtil.isBlank(userUuid)) {
            throw new IllegalArgumentException("用户UUID不能为空");
        }
    }

    /**
     * 默认备注
     */
    private String defaultRemark(String remark, String defaultRemark) {
        return StrUtil.isBlank(remark) ? defaultRemark : remark;
    }

    /**
     * 空积分值按零处理
     */
    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO_POINTS : value;
    }
}