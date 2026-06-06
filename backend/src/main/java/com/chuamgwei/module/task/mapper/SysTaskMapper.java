package com.chuamgwei.module.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.module.task.entity.SysTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步任务数据访问接口
 */
@Mapper
public interface SysTaskMapper extends BaseMapper<SysTask> {
}
