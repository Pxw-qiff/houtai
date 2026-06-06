package com.chuamgwei.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.infrastructure.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统配置配置项数据访问接口
 */
@Mapper
public interface SysConfigMapper extends BaseMapper<SysConfig> {
}
