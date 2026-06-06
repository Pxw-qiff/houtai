package com.chuamgwei.module.recharge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 充值订单实体类（对应 recharge_orders 表）
 */
@Data
@TableName("recharge_orders")
public class RechargeOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 唯一充值订单号 */
    private String orderNo;

    /** 第三方支付平台交易流水号 */
    private String tradeNo;

    /** 用户UUID */
    private String userUuid;

    /** 充值金额（元） */
    private BigDecimal amount;

    /** 到账积分 */
    private BigDecimal points;

    /** 充值比例，创建订单时从 sys_config 读取并固化 */
    private BigDecimal chargeRatio;

    /** 支付方式 */
    private String payType;

    /** 订单状态 */
    private String status;

    /** 支付过期时间 */
    private LocalDateTime expireTime;

    /** 支付成功时间 */
    private LocalDateTime payTime;

    /** 积分入账时间 */
    private LocalDateTime creditedTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}