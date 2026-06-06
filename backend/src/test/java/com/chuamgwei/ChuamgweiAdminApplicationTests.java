package com.chuamgwei;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.common.RequestContext;
import com.chuamgwei.common.Result;
import com.chuamgwei.infrastructure.entity.*;
import com.chuamgwei.infrastructure.mapper.*;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditFlow;
import com.chuamgwei.module.credit.mapper.CreditAccountMapper;
import com.chuamgwei.module.credit.mapper.CreditFlowMapper;
import com.chuamgwei.module.credit.service.CreditService;
import com.chuamgwei.module.recharge.controller.RechargeController;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.mapper.RechargeOrderMapper;
import com.chuamgwei.module.recharge.service.RechargeService;
import com.chuamgwei.module.task.entity.SysTask;
import com.chuamgwei.module.task.mapper.SysTaskMapper;
import com.chuamgwei.module.task.service.TaskService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创维 AI 网关管理后台全链路核心业务单元测试
 */
@SpringBootTest(properties = "auth.jwt-secret=chuamgwei-test-jwt-secret")
public class ChuamgweiAdminApplicationTests {

    @Autowired
    private CreditService creditService;

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private RechargeController rechargeController;

    @Autowired
    private TaskService taskService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RechargeOrderMapper rechargeOrderMapper;

    @Autowired
    private CreditAccountMapper creditAccountMapper;

    @Autowired
    private CreditFlowMapper creditFlowMapper;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Autowired
    private SysAuditLogMapper sysAuditLogMapper;

    @Autowired
    private SysTaskMapper sysTaskMapper;

    private String testUserUuid;
    private String testAdminUuid;

    private BigDecimal points(String value) {
        return new BigDecimal(value);
    }

    private void assertPoints(String expected, BigDecimal actual) {
        Assertions.assertEquals(0, points(expected).compareTo(actual));
    }

    @BeforeEach
    public void initData() {
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getUsername, "test_user_key"));
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getUsername, "test_admin_key"));

        testUserUuid = java.util.UUID.randomUUID().toString();

        User user = new User();
        user.setUserUuid(testUserUuid);
        user.setUsername("test_user_key");
        user.setIsBanned(0);
        user.setIsDeleted(0);
        user.setRegisterTime(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        creditAccountMapper.delete(Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid));
        CreditAccount account = new CreditAccount();
        account.setUserUuid(testUserUuid);
        account.setTotalPoints(points("1000000"));
        account.setAvailablePoints(points("1000000"));
        account.setFrozenPoints(points("0"));
        account.setTotalRechargePoints(points("0"));
        account.setTotalConsumePoints(points("0"));
        account.setTotalRefundPoints(points("0"));
        account.setStatus(1);
        account.setVersion(0);
        creditAccountMapper.insert(account);

        testAdminUuid = java.util.UUID.randomUUID().toString();
        User admin = new User();
        admin.setUserUuid(testAdminUuid);
        admin.setUsername("test_admin_key");
        admin.setAdminPermissions(2);
        admin.setIsBanned(0);
        admin.setIsDeleted(0);
        admin.setRegisterTime(LocalDateTime.now());
        admin.setCreatedAt(LocalDateTime.now());
        userMapper.insert(admin);

        sysConfigMapper.delete(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, "charge_ratio"));
        SysConfig ratioConfig = new SysConfig();
        ratioConfig.setConfigKey("charge_ratio");
        ratioConfig.setConfigValue("500000");
        ratioConfig.setRemark("充值兑换比例");
        ratioConfig.setUpdateTime(System.currentTimeMillis() / 1000);
        sysConfigMapper.insert(ratioConfig);

        sysConfigMapper.delete(Wrappers.<SysConfig>lambdaQuery().eq(SysConfig::getConfigKey, "task_video_fee"));
        SysConfig videoFeeConfig = new SysConfig();
        videoFeeConfig.setConfigKey("task_video_fee");
        videoFeeConfig.setConfigValue("1000000");
        videoFeeConfig.setRemark("生视频单次扣费额度");
        videoFeeConfig.setUpdateTime(System.currentTimeMillis() / 1000);
        sysConfigMapper.insert(videoFeeConfig);

        creditFlowMapper.delete(Wrappers.<CreditFlow>lambdaQuery().eq(CreditFlow::getUserUuid, testUserUuid));
        rechargeOrderMapper.delete(Wrappers.<RechargeOrder>lambdaQuery().eq(RechargeOrder::getUserUuid, testUserUuid));
        sysTaskMapper.delete(Wrappers.<SysTask>lambdaQuery().eq(SysTask::getUserUuid, testUserUuid));
        sysAuditLogMapper.delete(Wrappers.<SysAuditLog>lambdaQuery().eq(SysAuditLog::getOperatorUuid, testAdminUuid));
    }

    /**
     * 验证充值订单创建及支付回调的幂等性和积分入账、流水记录
     */
    @Test
    @Transactional
    public void testRechargeAndCallback() {
        BigDecimal amount = new BigDecimal("10.00");
        RechargeOrder order = rechargeService.createRechargeOrder(testUserUuid, amount, "ALIPAY");

        Assertions.assertNotNull(order);
        assertPoints("5000000", order.getPoints());
        Assertions.assertEquals("CREATED", order.getStatus());

        String result1 = rechargeService.handlePayCallback(order.getOrderNo(), "TRADE_MOCK_111", amount, "SUCCESS");
        Assertions.assertEquals("success", result1);

        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("6000000", account.getAvailablePoints());
        assertPoints("5000000", account.getTotalRechargePoints());

        RechargeOrder orderAfter = rechargeOrderMapper.selectById(order.getId());
        Assertions.assertEquals("CREDITED", orderAfter.getStatus());
        Assertions.assertEquals("TRADE_MOCK_111", orderAfter.getTradeNo());

        Long flowCount = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .eq(CreditFlow::getBizType, "RECHARGE")
                        .eq(CreditFlow::getBizOrderNo, order.getOrderNo())
        );
        Assertions.assertEquals(1L, flowCount);

        String result2 = rechargeService.handlePayCallback(order.getOrderNo(), "TRADE_MOCK_111", amount, "SUCCESS");
        Assertions.assertEquals("success", result2);

        CreditAccount accountFinal = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("6000000", accountFinal.getAvailablePoints());
    }

    /**
     * 验证支付失败回调只标记失败，不产生积分入账和流水
     */
    @Test
    @Transactional
    public void testRechargeFailedCallbackDoesNotCredit() {
        BigDecimal amount = new BigDecimal("10.00");
        RechargeOrder order = rechargeService.createRechargeOrder(testUserUuid, amount, "ALIPAY");

        String result = rechargeService.handlePayCallback(order.getOrderNo(), "TRADE_FAIL_111", amount, "FAILED");
        Assertions.assertEquals("success", result);

        RechargeOrder orderAfter = rechargeOrderMapper.selectById(order.getId());
        Assertions.assertEquals("FAILED", orderAfter.getStatus());

        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("1000000", account.getAvailablePoints());

        Long flowCount = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .eq(CreditFlow::getBizType, "RECHARGE")
                        .eq(CreditFlow::getBizOrderNo, order.getOrderNo())
        );
        Assertions.assertEquals(0L, flowCount);
    }

    /**
     * 验证支付成功回调金额不一致时拒绝入账
     */
    @Test
    @Transactional
    public void testRechargeCallbackRejectsAmountMismatch() {
        BigDecimal amount = new BigDecimal("10.00");
        RechargeOrder order = rechargeService.createRechargeOrder(testUserUuid, amount, "ALIPAY");

        RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                () -> rechargeService.handlePayCallback(order.getOrderNo(), "TRADE_BAD_AMOUNT_111",
                        new BigDecimal("9.99"), "SUCCESS"));
        Assertions.assertEquals("支付金额不一致", exception.getMessage());
    }

    /**
     * 验证已超时订单收到成功回调时不会继续入账
     */
    @Test
    @Transactional
    public void testRechargeCallbackSkipsTimeoutOrder() {
        BigDecimal amount = new BigDecimal("10.00");
        RechargeOrder order = rechargeService.createRechargeOrder(testUserUuid, amount, "ALIPAY");
        order.setStatus("TIMEOUT");
        rechargeOrderMapper.updateById(order);

        String result = rechargeService.handlePayCallback(order.getOrderNo(), "TRADE_TIMEOUT_111", amount, "SUCCESS");
        Assertions.assertEquals("success", result);

        RechargeOrder orderAfter = rechargeOrderMapper.selectById(order.getId());
        Assertions.assertEquals("TIMEOUT", orderAfter.getStatus());

        CreditAccount account = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("1000000", account.getAvailablePoints());

        Long flowCount = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .eq(CreditFlow::getBizType, "RECHARGE")
                        .eq(CreditFlow::getBizOrderNo, order.getOrderNo())
        );
        Assertions.assertEquals(0L, flowCount);
    }

    /**
     * 验证当前用户只能查询自己的充值订单
     */
    @Test
    @Transactional
    public void testRechargeOrderOwnerAccessOnly() {
        BigDecimal amount = new BigDecimal("10.00");
        RechargeOrder order = rechargeService.createRechargeOrder(testUserUuid, amount, "ALIPAY");

        RequestContext.setOperatorUuid(java.util.UUID.randomUUID().toString());
        try {
            RuntimeException exception = Assertions.assertThrows(RuntimeException.class,
                    () -> rechargeController.getOrder(order.getOrderNo()));
            Assertions.assertEquals("无权访问该充值订单", exception.getMessage());
        } finally {
            RequestContext.clear();
        }

        RequestContext.setOperatorUuid(testUserUuid);
        try {
            Result<RechargeOrder> result = rechargeController.getOrder(order.getOrderNo());
            Assertions.assertEquals(order.getOrderNo(), result.getData().getOrderNo());
        } finally {
            RequestContext.clear();
        }
    }

    /**
     * 验证管理员调账与操作审计
     */
    @Test
    @Transactional
    public void testAdminAdjustBalance() {
        boolean successAdd = creditService.adjustBalance(testUserUuid, 1, points("200000"), "测试客服补偿",
                testAdminUuid, "测试管理员");
        Assertions.assertTrue(successAdd);

        CreditAccount accountAfterAdd = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("1200000", accountAfterAdd.getAvailablePoints());

        boolean successSub = creditService.adjustBalance(testUserUuid, 2, points("500000"), "测试风控扣减",
                testAdminUuid, "测试管理员");
        Assertions.assertTrue(successSub);

        CreditAccount accountAfterSub = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("700000", accountAfterSub.getAvailablePoints());

        Long auditCount = sysAuditLogMapper.selectCount(
                Wrappers.<SysAuditLog>lambdaQuery().eq(SysAuditLog::getOperatorUuid, testAdminUuid)
        );
        Assertions.assertEquals(2L, auditCount);

        Long flowCount = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .in(CreditFlow::getBizType, "ADMIN_ADD", "ADMIN_DEDUCT")
        );
        Assertions.assertEquals(2L, flowCount);
    }

    /**
     * 验证生视频异步任务提交与退款机制
     */
    @Test
    @Transactional
    public void testVideoTaskAndRefund() {
        SysTask task = taskService.submitVideoTask(testUserUuid, "kling-v1-5", "一只奔跑的哈士奇",
                "模糊", "16:9", "5s");
        Assertions.assertNotNull(task);
        Assertions.assertEquals("SUBMITTED", task.getStatus());

        CreditAccount accountAfterSubmit = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("0", accountAfterSubmit.getAvailablePoints());
        assertPoints("1000000", accountAfterSubmit.getFrozenPoints());

        Long countFreeze = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .eq(CreditFlow::getBizType, "TASK_FREEZE")
        );
        Assertions.assertEquals(1L, countFreeze);

        SysTask taskInDb = sysTaskMapper.selectById(task.getId());
        ((com.chuamgwei.module.task.service.impl.TaskServiceImpl) taskService)
                .handleTaskFailure(taskInDb, "模拟超时失败");

        CreditAccount accountAfterRefund = creditAccountMapper.selectOne(
                Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, testUserUuid)
        );
        assertPoints("1000000", accountAfterRefund.getAvailablePoints());
        assertPoints("0", accountAfterRefund.getFrozenPoints());
        assertPoints("1000000", accountAfterRefund.getTotalRefundPoints());

        Long countReturn = creditFlowMapper.selectCount(
                Wrappers.<CreditFlow>lambdaQuery()
                        .eq(CreditFlow::getUserUuid, testUserUuid)
                        .eq(CreditFlow::getBizType, "TASK_RETURN")
        );
        Assertions.assertEquals(1L, countReturn);
    }
}