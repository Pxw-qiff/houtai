package com.chuamgwei.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户积分变动流水实体类（对应 balance_flow 表，后续将迁移到 credit_flow）
 */
@Data
@TableName("balance_flow")
public class BalanceFlow implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 唯一流水号 */
    private String flowNo;

    /** 用户UUID */
    private String userUuid;

    /** 变动前额度 */
    private Long beforeQuota;

    /** 变动额度（正数为增加，负数为减少） */
    private Long changeQuota;

    /** 变动后额度 */
    private Long afterQuota;

    /** 业务类型 */
    private String bizType;

    /** 关联业务单号 */
    private String bizOrderNo;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;

    /** 创建时间戳 */
    private Long createTime;
}