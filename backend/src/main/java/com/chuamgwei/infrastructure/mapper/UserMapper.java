package com.chuamgwei.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.infrastructure.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问接口（共享 users 表只读查询）
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 查询鉴权和用户状态校验所需字段，避免读取老表中类型不一致的时间字段
     */
    @Select("SELECT user_uuid, username, phone, email, user_permission_level, admin_permissions, "
            + "is_trial_user, is_online, is_banned, ban_reason, is_deleted "
            + "FROM users WHERE user_uuid = #{userUuid} LIMIT 1")
    User selectAuthUserByUuid(@Param("userUuid") String userUuid);
}
