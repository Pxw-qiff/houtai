package com.chuamgwei.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.infrastructure.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志数据访问接口
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
