package com.chuamgwei.module.task.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.infrastructure.entity.SysConfig;
import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.SysConfigMapper;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditFlow;
import com.chuamgwei.module.credit.mapper.CreditAccountMapper;
import com.chuamgwei.module.credit.mapper.CreditFlowMapper;
import com.chuamgwei.module.task.entity.SysTask;
import com.chuamgwei.module.task.mapper.SysTaskMapper;
import com.chuamgwei.module.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 生图与生视频任务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final int POINT_SCALE = 6;
    private static final BigDecimal ZERO_POINTS = BigDecimal.ZERO.setScale(POINT_SCALE, RoundingMode.HALF_UP);

    private final SysTaskMapper sysTaskMapper;
    private final UserMapper userMapper;
    private final CreditAccountMapper creditAccountMapper;
    private final CreditFlowMapper creditFlowMapper;
    private final SysConfigMapper sysConfigMapper;

    private final Random random = new Random();

    /**
     * 从系统配置中获取单次生图扣费额度（积分）
     */
    private BigDecimal getImageTaskFee() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, "task_image_fee")
        );
        if (config == null) {
            return normalizePoints(new BigDecimal("250000"));
        }
        return normalizePoints(new BigDecimal(config.getConfigValue()));
    }

    /**
     * 从系统配置中获取单次生视频扣费额度（积分）
     */
    private BigDecimal getVideoTaskFee() {
        SysConfig config = sysConfigMapper.selectOne(
                Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, "task_video_fee")
        );
        if (config == null) {
            return normalizePoints(new BigDecimal("1000000"));
        }
        return normalizePoints(new BigDecimal(config.getConfigValue()));
    }

    /**
     * 确保积分账户存在（懒创建）
     */
    private CreditAccount getOrCreateAccount(String userUuid) {
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

    /**
     * 校验用户是否可以提交任务
     */
    private void checkUserValid(String userUuid) {
        User user = userMapper.selectById(userUuid);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (user.getIsBanned() != null && user.getIsBanned() != 0) {
            throw new RuntimeException("用户已被封禁，无法发起任务");
        }
        if (user.getIsDeleted() != null && user.getIsDeleted() != 0) {
            throw new RuntimeException("用户账号已注销");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTask submitImageTask(String userUuid, String model, String prompt, String negativePrompt, String size) {
        log.info("用户 {} 提交异步生图任务. 模型: {}, 提示词: {}", userUuid, model, prompt);

        BigDecimal fee = getImageTaskFee();
        checkUserValid(userUuid);
        freezeQuota(userUuid, fee, "生图任务冻结");

        SysTask task = new SysTask();
        task.setTaskId("task_" + IdUtil.simpleUUID());
        task.setUserUuid(userUuid);
        task.setPlatform("MJ");
        task.setAction("generate");
        task.setStatus("SUBMITTED");
        task.setQuota(fee);
        task.setCreatedAt(System.currentTimeMillis() / 1000);
        task.setUpdatedAt(System.currentTimeMillis() / 1000);

        String mockUpstreamId = "mj_" + IdUtil.nanoId();
        task.setUpstreamTaskId(mockUpstreamId);

        sysTaskMapper.insert(task);
        log.info("生图任务提交成功. 任务ID: {}, 上游任务ID: {}", task.getTaskId(), mockUpstreamId);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysTask submitVideoTask(String userUuid, String model, String prompt, String negativePrompt,
                                    String aspectRatio, String duration) {
        log.info("用户 {} 提交异步生视频任务. 模型: {}, 提示词: {}", userUuid, model, prompt);

        BigDecimal fee = getVideoTaskFee();
        checkUserValid(userUuid);
        freezeQuota(userUuid, fee, "生视频任务冻结");

        SysTask task = new SysTask();
        task.setTaskId("task_" + IdUtil.simpleUUID());
        task.setUserUuid(userUuid);
        task.setPlatform("KLING");
        if (model.toLowerCase().contains("luma")) {
            task.setPlatform("LUMA");
        }
        task.setAction("generate");
        task.setStatus("SUBMITTED");
        task.setQuota(fee);
        task.setCreatedAt(System.currentTimeMillis() / 1000);
        task.setUpdatedAt(System.currentTimeMillis() / 1000);

        String mockUpstreamId = "vid_" + IdUtil.nanoId();
        task.setUpstreamTaskId(mockUpstreamId);

        sysTaskMapper.insert(task);
        log.info("生视频任务提交成功. 任务ID: {}, 上游任务ID: {}", task.getTaskId(), mockUpstreamId);
        return task;
    }

    @Override
    public SysTask getTaskStatus(String taskId) {
        SysTask task = sysTaskMapper.selectOne(
                Wrappers.<SysTask>lambdaQuery().eq(SysTask::getTaskId, taskId)
        );
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return task;
    }

    /**
     * 冻结用户可用积分
     */
    private void freezeQuota(String userUuid, BigDecimal amount, String remark) {
        BigDecimal normalizedAmount = normalizePoints(amount);
        CreditAccount account = getOrCreateAccount(userUuid);
        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        BigDecimal afterAvailable = beforeAvailable.subtract(normalizedAmount);
        BigDecimal afterFrozen = beforeFrozen.add(normalizedAmount);

        int rows = creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
                        .ge(CreditAccount::getAvailablePoints, normalizedAmount)
        );

        if (rows == 0) {
            log.warn("用户 {} 积分不足。需要: {}, 实际可用: {}", userUuid, normalizedAmount, beforeAvailable);
            throw new RuntimeException("您的账户积分不足以发起本次任务，请先充值");
        }

        CreditFlow flow = new CreditFlow();
        flow.setFlowNo("FLW" + IdUtil.getSnowflakeNextIdStr());
        flow.setUserUuid(userUuid);
        flow.setAccountId(account.getId());
        flow.setBeforeAvailablePoints(beforeAvailable);
        flow.setChangeAvailablePoints(normalizedAmount.negate());
        flow.setAfterAvailablePoints(afterAvailable);
        flow.setBeforeFrozenPoints(beforeFrozen);
        flow.setChangeFrozenPoints(normalizedAmount);
        flow.setAfterFrozenPoints(afterFrozen);
        flow.setBizType("TASK_FREEZE");
        flow.setBizOrderNo("FREEZE_" + IdUtil.simpleUUID());
        flow.setOperatorName("system");
        flow.setRemark(remark);
        flow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(flow);
    }

    /**
     * 定时轮询上游任务状态，并在任务状态发生变化时处理退款或扣减
     */
    @Override
    public void pollPendingTasks() {
        List<SysTask> pendingTasks = sysTaskMapper.selectList(
                Wrappers.<SysTask>lambdaQuery()
                        .in(SysTask::getStatus, "SUBMITTED", "QUEUED", "IN_PROGRESS")
        );

        if (pendingTasks.isEmpty()) {
            return;
        }

        log.debug("轮询引擎正在扫描未完结的生图/生视频任务，总计: {} 个", pendingTasks.size());

        for (SysTask task : pendingTasks) {
            try {
                long elapsedSeconds = (System.currentTimeMillis() / 1000) - task.getCreatedAt();
                if (elapsedSeconds < 15) {
                    if ("SUBMITTED".equals(task.getStatus())) {
                        task.setStatus("IN_PROGRESS");
                        task.setUpdatedAt(System.currentTimeMillis() / 1000);
                        sysTaskMapper.updateById(task);
                    }
                    continue;
                }

                if (random.nextInt(100) < 85) {
                    handleTaskSuccess(task);
                } else {
                    handleTaskFailure(task, "上游接口生成超时或审核不通过");
                }
            } catch (Exception e) {
                log.error("轮询处理任务 {} 发生异常: ", task.getTaskId(), e);
            }
        }
    }

    /**
     * 任务成功，扣除冻结积分，累加消费
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleTaskSuccess(SysTask task) {
        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery()
                        .eq(CreditAccount::getUserUuid, task.getUserUuid())
                        .last("for update")
        );
        if (account == null) {
            log.error("处理任务成功异常: 用户 {} 积分账户不存在", task.getUserUuid());
            return;
        }

        BigDecimal amount = normalizePoints(task.getQuota());
        BigDecimal availablePoints = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        BigDecimal afterFrozen = beforeFrozen.subtract(amount);
        BigDecimal afterConsume = valueOrZero(account.getTotalConsumePoints()).add(amount);

        creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getTotalConsumePoints, afterConsume)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
        );

        task.setStatus("SUCCESS");
        if ("MJ".equals(task.getPlatform())) {
            task.setResultUrl("https://images.unsplash.com/photo-1579546929518-9e396f3cc809?q=80&w=640");
        } else {
            task.setResultUrl("https://www.w3school.com.cn/i/video/shanghai.mp4");
        }
        task.setUpdatedAt(System.currentTimeMillis() / 1000);
        sysTaskMapper.updateById(task);

        CreditFlow consumeFlow = new CreditFlow();
        consumeFlow.setFlowNo("FLW" + IdUtil.getSnowflakeNextIdStr());
        consumeFlow.setUserUuid(task.getUserUuid());
        consumeFlow.setAccountId(account.getId());
        consumeFlow.setBeforeAvailablePoints(availablePoints);
        consumeFlow.setChangeAvailablePoints(ZERO_POINTS);
        consumeFlow.setAfterAvailablePoints(availablePoints);
        consumeFlow.setBeforeFrozenPoints(beforeFrozen);
        consumeFlow.setChangeFrozenPoints(amount.negate());
        consumeFlow.setAfterFrozenPoints(afterFrozen);
        consumeFlow.setBizType("CONSUME");
        consumeFlow.setBizOrderNo(task.getTaskId());
        consumeFlow.setOperatorName("system");
        consumeFlow.setRemark(task.getPlatform() + "任务生成成功，实际扣费");
        consumeFlow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(consumeFlow);

        CreditFlow unfreezeFlow = new CreditFlow();
        unfreezeFlow.setFlowNo("FLW" + IdUtil.getSnowflakeNextIdStr());
        unfreezeFlow.setUserUuid(task.getUserUuid());
        unfreezeFlow.setAccountId(account.getId());
        unfreezeFlow.setBeforeAvailablePoints(availablePoints);
        unfreezeFlow.setChangeAvailablePoints(ZERO_POINTS);
        unfreezeFlow.setAfterAvailablePoints(availablePoints);
        unfreezeFlow.setBeforeFrozenPoints(afterFrozen);
        unfreezeFlow.setChangeFrozenPoints(ZERO_POINTS);
        unfreezeFlow.setAfterFrozenPoints(afterFrozen);
        unfreezeFlow.setBizType("TASK_UNFREEZE");
        unfreezeFlow.setBizOrderNo(task.getTaskId());
        unfreezeFlow.setOperatorName("system");
        unfreezeFlow.setRemark("任务解冻");
        unfreezeFlow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(unfreezeFlow);

        log.info("异步任务生成成功，扣费入账完成. 任务ID: {} 用户: {} 冻结扣除: {}",
                task.getTaskId(), task.getUserUuid(), amount);
    }

    /**
     * 任务失败，返还冻结积分到可用余额
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleTaskFailure(SysTask task, String failReason) {
        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery()
                        .eq(CreditAccount::getUserUuid, task.getUserUuid())
                        .last("for update")
        );
        if (account == null) {
            log.error("处理任务失败异常: 用户 {} 积分账户不存在", task.getUserUuid());
            return;
        }

        BigDecimal amount = normalizePoints(task.getQuota());
        BigDecimal beforeAvailable = valueOrZero(account.getAvailablePoints());
        BigDecimal beforeFrozen = valueOrZero(account.getFrozenPoints());
        BigDecimal afterAvailable = beforeAvailable.add(amount);
        BigDecimal afterFrozen = beforeFrozen.subtract(amount);
        BigDecimal afterRefund = valueOrZero(account.getTotalRefundPoints()).add(amount);

        creditAccountMapper.update(null,
                Wrappers.<CreditAccount>lambdaUpdate()
                        .set(CreditAccount::getAvailablePoints, afterAvailable)
                        .set(CreditAccount::getFrozenPoints, afterFrozen)
                        .set(CreditAccount::getTotalRefundPoints, afterRefund)
                        .set(CreditAccount::getVersion, account.getVersion() + 1)
                        .eq(CreditAccount::getId, account.getId())
        );

        task.setStatus("FAILURE");
        task.setFailReason(failReason);
        task.setUpdatedAt(System.currentTimeMillis() / 1000);
        sysTaskMapper.updateById(task);

        CreditFlow returnFlow = new CreditFlow();
        returnFlow.setFlowNo("FLW" + IdUtil.getSnowflakeNextIdStr());
        returnFlow.setUserUuid(task.getUserUuid());
        returnFlow.setAccountId(account.getId());
        returnFlow.setBeforeAvailablePoints(beforeAvailable);
        returnFlow.setChangeAvailablePoints(amount);
        returnFlow.setAfterAvailablePoints(afterAvailable);
        returnFlow.setBeforeFrozenPoints(beforeFrozen);
        returnFlow.setChangeFrozenPoints(amount.negate());
        returnFlow.setAfterFrozenPoints(afterFrozen);
        returnFlow.setBizType("TASK_RETURN");
        returnFlow.setBizOrderNo(task.getTaskId());
        returnFlow.setOperatorName("system");
        returnFlow.setRemark(task.getPlatform() + "任务生成失败，积分全额返还");
        returnFlow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(returnFlow);

        CreditFlow unfreezeFlow = new CreditFlow();
        unfreezeFlow.setFlowNo("FLW" + IdUtil.getSnowflakeNextIdStr());
        unfreezeFlow.setUserUuid(task.getUserUuid());
        unfreezeFlow.setAccountId(account.getId());
        unfreezeFlow.setBeforeAvailablePoints(afterAvailable);
        unfreezeFlow.setChangeAvailablePoints(ZERO_POINTS);
        unfreezeFlow.setAfterAvailablePoints(afterAvailable);
        unfreezeFlow.setBeforeFrozenPoints(afterFrozen);
        unfreezeFlow.setChangeFrozenPoints(ZERO_POINTS);
        unfreezeFlow.setAfterFrozenPoints(afterFrozen);
        unfreezeFlow.setBizType("TASK_UNFREEZE");
        unfreezeFlow.setBizOrderNo(task.getTaskId());
        unfreezeFlow.setOperatorName("system");
        unfreezeFlow.setRemark("任务解冻");
        unfreezeFlow.setCreatedAt(LocalDateTime.now());
        creditFlowMapper.insert(unfreezeFlow);

        log.warn("异步任务生成失败，积分已原路退回. 任务ID: {}, 用户: {}, 退回: {}, 变动前可用: {}, 变动后可用: {}",
                task.getTaskId(), task.getUserUuid(), amount, beforeAvailable, afterAvailable);
    }

    /**
     * 标准化任务积分数量
     */
    private BigDecimal normalizePoints(BigDecimal points) {
        if (points == null || points.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("任务积分必须大于零");
        }
        return points.setScale(POINT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 空积分值按零处理
     */
    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? ZERO_POINTS : value;
    }
}