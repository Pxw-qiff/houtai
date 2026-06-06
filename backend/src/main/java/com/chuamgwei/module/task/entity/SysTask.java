package com.chuamgwei.module.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 生图与生视频异步任务流水实体类（对应 sys_task 表）
 */
@Data
@TableName("sys_task")
public class SysTask implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 对外暴露的任务ID */
    private String taskId;

    /** 上游服务商真实任务ID */
    private String upstreamTaskId;

    /** 用户UUID */
    private String userUuid;

    /** 平台类型 */
    private String platform;

    /** 任务动作 */
    private String action;

    /** 任务状态 */
    private String status;

    /** 预扣积分 */
    private BigDecimal quota;

    /** 生成结果URL */
    private String resultUrl;

    /** 失败原因 */
    private String failReason;

    /** 创建时间戳 */
    private Long createdAt;

    /** 更新时间戳 */
    private Long updatedAt;
}