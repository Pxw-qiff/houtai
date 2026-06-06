package com.chuamgwei.module.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分账户实体类（对应 credit_account 表）
 */
@Data
@TableName("credit_account")
public class CreditAccount implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户UUID */
    private String userUuid;

    /** 总积分 */
    private BigDecimal totalPoints;

    /** 可用积分 */
    private BigDecimal availablePoints;

    /** 冻结积分 */
    private BigDecimal frozenPoints;

    /** 累计充值积分 */
    private BigDecimal totalRechargePoints;

    /** 累计消费积分 */
    private BigDecimal totalConsumePoints;

    /** 累计退款积分 */
    private BigDecimal totalRefundPoints;

    /** 账户状态：1-正常，2-冻结 */
    private Integer status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}