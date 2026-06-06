package com.chuamgwei.module.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditAccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 积分账户数据访问接口
 */
@Mapper
public interface CreditAccountMapper extends BaseMapper<CreditAccount> {

    /**
     * 分页查询积分账户列表，LEFT JOIN users 表取展示字段
     */
    @Select("SELECT ca.id, ca.user_uuid, u.username, u.email, u.phone, u.admin_permissions, u.is_banned, "
            + "ca.total_points, ca.available_points, ca.frozen_points, "
            + "ca.total_recharge_points, ca.total_consume_points, ca.total_refund_points, "
            + "ca.status, ca.version, ca.created_at, ca.updated_at "
            + "FROM credit_account ca "
            + "LEFT JOIN users u ON ca.user_uuid = u.user_uuid "
            + "ORDER BY ca.id DESC")
    Page<CreditAccountVO> selectAccountPage(Page<CreditAccountVO> page);

    /**
     * 按用户名或邮箱搜索
     */
    @Select("<script>"
            + "SELECT ca.id, ca.user_uuid, u.username, u.email, u.phone, u.admin_permissions, u.is_banned, "
            + "ca.total_points, ca.available_points, ca.frozen_points, "
            + "ca.total_recharge_points, ca.total_consume_points, ca.total_refund_points, "
            + "ca.status, ca.version, ca.created_at, ca.updated_at "
            + "FROM credit_account ca "
            + "LEFT JOIN users u ON ca.user_uuid = u.user_uuid "
            + "<where>"
            + "<if test='keyword != null and keyword != \"\"'>"
            + "AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%'))"
            + "</if>"
            + "</where>"
            + "ORDER BY ca.id DESC"
            + "</script>")
    Page<CreditAccountVO> searchAccountPage(Page<CreditAccountVO> page, @Param("keyword") String keyword);
}