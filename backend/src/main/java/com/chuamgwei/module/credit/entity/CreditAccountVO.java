package com.chuamgwei.module.credit.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分账户列表 VO（JOIN users 表取用户名/邮箱等展示字段）
 */
@Data
public class CreditAccountVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 账户ID */
    private Long id;
    /** 用户UUID */
    private String userUuid;
    /** 用户名 */
    private String username;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 管理员权限 */
    private Integer adminPermissions;
    /** 封禁状态 */
    private Integer isBanned;

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
    /** 账户状态 */
    private Integer status;
    /** 版本号 */
    private Integer version;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 更新时间 */
    private LocalDateTime updatedAt;
}