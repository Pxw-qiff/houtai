package com.chuamgwei.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 全局系统业务配置实体类 (对应sys_config表)
 */
@Data
@TableName("sys_config")
public class SysConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 配置键
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置备注说明
     */
    private String remark;

    /**
     * 更新时间戳
     */
    private Long updateTime;
}
