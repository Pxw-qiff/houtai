package com.chuamgwei.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 系统管理员操作审计日志实体类（对应 sys_audit_log 表）
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人用户UUID */
    private String operatorUuid;

    /** 操作人用户名 */
    private String operatorName;

    /** 操作动作 */
    private String action;

    /** 操作目标 */
    private String target;

    /** 操作人IP */
    private String ip;

    /** 操作请求参数 */
    private String params;

    /** 操作结果 */
    private String result;

    /** 创建时间戳 */
    private Long createTime;
}