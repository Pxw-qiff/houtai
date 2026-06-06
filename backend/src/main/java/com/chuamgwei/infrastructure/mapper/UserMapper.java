package com.chuamgwei.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.infrastructure.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口（共享 users 表只读查询）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
