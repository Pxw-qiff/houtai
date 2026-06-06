package com.chuamgwei.module.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分流水实体类（对应 credit_flow 表，只增不改不删）
 */
@Data
@TableName("credit_flow")
public class CreditFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 唯一流水号 */
    private String flowNo;

    /** 用户UUID */
    private String userUuid;

    /** 积分账户ID */
    private Long accountId;

    /** 变动前可用积分 */
    private BigDecimal beforeAvailablePoints;

    /** 可用积分变动量 */
    private BigDecimal changeAvailablePoints;

    /** 变动后可用积分 */
    private BigDecimal afterAvailablePoints;

    /** 变动前冻结积分 */
    private BigDecimal beforeFrozenPoints;

    /** 冻结积分变动量 */
    private BigDecimal changeFrozenPoints;

    /** 变动后冻结积分 */
    private BigDecimal afterFrozenPoints;

    /** 业务类型 */
    private String bizType;

    /** 关联业务单号 */
    private String bizOrderNo;

    /** 操作人UUID */
    private String operatorUuid;

    /** 操作人展示名称 */
    private String operatorName;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;
}