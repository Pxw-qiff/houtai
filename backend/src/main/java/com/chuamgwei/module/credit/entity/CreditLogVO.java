package com.chuamgwei.module.credit.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端统一积分日志展示对象
 */
@Data
public class CreditLogVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 日志展示ID */
    private String logId;

    /** 用户UUID */
    private String userUuid;

    /** 日志类型 */
    private String type;

    /** 日志类型文案 */
    private String typeText;

    /** 积分方向：IN-增加，OUT-减少 */
    private String direction;

    /** 展示积分数量 */
    private BigDecimal amount;

    /** 展示标题 */
    private String title;

    /** 业务状态 */
    private String status;

    /** 业务状态文案 */
    private String statusText;

    /** 原始业务类型 */
    private String bizType;

    /** 关联业务单号 */
    private String bizOrderNo;

    /** 变动前可用积分 */
    private BigDecimal balanceBefore;

    /** 变动后可用积分 */
    private BigDecimal balanceAfter;

    /** 操作人展示名称 */
    private String operatorName;

    /** 展示备注 */
    private String remark;

    /** 日志创建时间 */
    private LocalDateTime createdAt;
}