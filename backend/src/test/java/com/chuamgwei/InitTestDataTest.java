package com.chuamgwei;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.mapper.CreditAccountMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 数据库测试数据初始化（不自动回滚，跑一次即写入真实库）
 */
@Disabled("手动初始化真实库数据，禁止参与自动回归")
@SpringBootTest
public class InitTestDataTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CreditAccountMapper creditAccountMapper;

    /** 前端 request.js 硬编码的操作人 UUID，保持一致 */
    private static final String TEST_OPERATOR_UUID = "admin-default";
    private static final String TEST_USERNAME = "测试管理员";
    private static final String TEST_EMAIL = "admin@test.local";

    /** 普通测试用户 */
    private static final String TEST_USER_UUID = "test-user-001";
    private static final String TEST_USER_NAME = "测试用户";
    private static final String TEST_USER_EMAIL = "user@test.local";

    private BigDecimal points(String value) {
        return new BigDecimal(value);
    }

    @Test
    public void insertTestAdmin() {
        // 先删后插，保证可重复执行
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getUserUuid, TEST_OPERATOR_UUID));
        creditAccountMapper.delete(Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, TEST_OPERATOR_UUID));

        User admin = new User();
        admin.setUserUuid(TEST_OPERATOR_UUID);
        admin.setUsername(TEST_USERNAME);
        admin.setEmail(TEST_EMAIL);
        admin.setAdminPermissions(2); // 管理员
        admin.setIsBanned(0);
        admin.setIsDeleted(0);
        admin.setRegisterTime(LocalDateTime.now());
        admin.setCreatedAt(LocalDateTime.now());
        userMapper.insert(admin);

        CreditAccount adminAccount = new CreditAccount();
        adminAccount.setUserUuid(TEST_OPERATOR_UUID);
        adminAccount.setTotalPoints(points("0"));
        adminAccount.setAvailablePoints(points("0"));
        adminAccount.setFrozenPoints(points("0"));
        adminAccount.setTotalRechargePoints(points("0"));
        adminAccount.setTotalConsumePoints(points("0"));
        adminAccount.setTotalRefundPoints(points("0"));
        adminAccount.setStatus(1);
        adminAccount.setVersion(0);
        creditAccountMapper.insert(adminAccount);

        System.out.println("管理员已插入: user_uuid=" + TEST_OPERATOR_UUID + ", username=" + TEST_USERNAME);
    }

    @Test
    public void insertTestUser() {
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getUserUuid, TEST_USER_UUID));
        creditAccountMapper.delete(Wrappers.<CreditAccount>lambdaQuery().eq(CreditAccount::getUserUuid, TEST_USER_UUID));

        User user = new User();
        user.setUserUuid(TEST_USER_UUID);
        user.setUsername(TEST_USER_NAME);
        user.setEmail(TEST_USER_EMAIL);
        user.setAdminPermissions(1); // 普通用户
        user.setIsBanned(0);
        user.setIsDeleted(0);
        user.setRegisterTime(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        userMapper.insert(user);

        CreditAccount userAccount = new CreditAccount();
        userAccount.setUserUuid(TEST_USER_UUID);
        userAccount.setTotalPoints(points("1000000"));
        userAccount.setAvailablePoints(points("1000000"));
        userAccount.setFrozenPoints(points("0"));
        userAccount.setTotalRechargePoints(points("0"));
        userAccount.setTotalConsumePoints(points("0"));
        userAccount.setTotalRefundPoints(points("0"));
        userAccount.setStatus(1);
        userAccount.setVersion(0);
        creditAccountMapper.insert(userAccount);

        System.out.println("普通用户已插入: user_uuid=" + TEST_USER_UUID + ", username=" + TEST_USER_NAME + ", 积分=1000000");
    }

    @Test
    public void initAll() {
        insertTestAdmin();
        insertTestUser();
        System.out.println("全部测试数据初始化完成");
    }
}