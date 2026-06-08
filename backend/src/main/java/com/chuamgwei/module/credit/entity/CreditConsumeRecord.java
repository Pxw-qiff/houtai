package com.chuamgwei.module.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户端消费记录读模型
 */
@Data
@TableName("credit_consume_record")
public class CreditConsumeRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户UUID */
    private String userUuid;

    /** 业务类型 */
    private String bizType;

    /** 业务单号 */
    private String bizOrderNo;

    /** 展示标题 */
    private String title;

    /** 结算状态 */
    private String status;

    /** 状态文案 */
    private String statusText;

    /** 预扣积分 */
    private BigDecimal preDeductPoints;

    /** 实际消费积分 */
    private BigDecimal actualCostPoints;

    /** 返还积分 */
    private BigDecimal refundPoints;

    /** 结算补扣积分 */
    private BigDecimal extraDeductPoints;

    /** 当前冻结积分 */
    private BigDecimal frozenPoints;

    /** 业务开始前可用积分 */
    private BigDecimal balanceBefore;

    /** 当前阶段后可用积分 */
    private BigDecimal balanceAfter;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime finishedAt;

    /** 最近更新时间 */
    private LocalDateTime latestAt;

    /** 展示备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}