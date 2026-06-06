package com.chuamgwei.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类（对应共享 users 表，只读查询用途）
 * users 表由服务端登陆方案管理，Java 积分系统不接管用户增删改
 */
@Data
@TableName("users")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户UUID主键 */
    @TableId
    private String userUuid;

    /** 用户名 */
    private String username;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 权益等级：0-无，1-一级，2-二级，3-三级，10-无穷 */
    private Integer userPermissionLevel;

    /** 管理员权限：1-普通用户, 2-管理员, 3-超级管理员 */
    private Integer adminPermissions;

    /** 是否体验用户 */
    private Integer isTrialUser;

    /** 是否在线 */
    private Integer isOnline;

    /** 封禁状态：0-正常，1-临时封禁，2-永久封禁 */
    private Integer isBanned;

    /** 封禁原因 */
    private String banReason;

    /** 软删除标记 */
    private Integer isDeleted;

    /** 账号到期时间 */
    private LocalDateTime expireTime;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 最后一次登录时间 */
    private LocalDateTime lastLoginTime;

    /** 记录创建时间 */
    private LocalDateTime createdAt;

    /** 记录更新时间 */
    private LocalDateTime updatedAt;
}