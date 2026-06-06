package com.chuamgwei.module.credit.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 内部计费接口返回结果
 */
@Data
public class CreditBillingResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户UUID */
    private String userUuid;

    /** 业务类型 */
    private String bizType;

    /** 业务单号 */
    private String bizOrderNo;

    /** 生命周期状态 */
    private String status;

    /** 本次请求积分 */
    private BigDecimal requestedPoints;

    /** 预扣积分 */
    private BigDecimal preConsumedPoints;

    /** 实际结算积分 */
    private BigDecimal actualPoints;

    /** 当前可用积分 */
    private BigDecimal availablePoints;

    /** 当前冻结积分 */
    private BigDecimal frozenPoints;

    /** 是否幂等返回 */
    private Boolean idempotent;

    /** 结果说明 */
    private String message;
}