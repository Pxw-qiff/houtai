package com.chuamgwei.module.credit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditAccountVO;
import com.chuamgwei.module.credit.entity.CreditBillingResult;
import com.chuamgwei.module.credit.entity.CreditConsumeRecord;
import com.chuamgwei.module.credit.entity.CreditFlow;

import java.math.BigDecimal;

/**
 * 积分账户业务接口
 */
public interface CreditService {

    /**
     * 获取或懒创建积分账户
     */
    CreditAccount getOrCreateAccount(String userUuid);

    /**
     * 查询用户积分余额
     */
    CreditAccount getBalance(String userUuid);

    /**
     * 内部预扣积分
     */
    CreditBillingResult preConsume(String userUuid, String bizType, String bizOrderNo,
                                   BigDecimal estimatedPoints, String remark);

    /**
     * 内部结算积分
     */
    CreditBillingResult settle(String userUuid, String bizType, String bizOrderNo,
                               BigDecimal actualPoints, String remark);

    /**
     * 内部退款积分
     */
    CreditBillingResult refund(String userUuid, String bizType, String bizOrderNo, String remark);

    /**
     * 积分入账（充值到账时调用）
     */
    CreditAccount addPoints(String userUuid, BigDecimal points, String bizOrderNo, String remark);

    /**
     * 管理员手动调账
     */
    boolean adjustBalance(String userUuid, Integer adjustType, BigDecimal points, String reason,
                          String operatorUuid, String operatorName);

    /**
     * 分页查询积分账户列表（JOIN users 展示）
     */
    Page<CreditAccountVO> pageAccounts(Integer current, Integer size, String keyword);

    /**
     * 分页查询用户端消费记录
     */
    Page<CreditConsumeRecord> pageConsumeRecords(Integer current, Integer size, String userUuid);

    /**
     * 分页查询积分流水（支持按用户UUID、业务类型筛选）
     */
    Page<CreditFlow> pageFlows(Integer current, Integer size, String userUuid, String bizType);
}