package com.chuamgwei.module.recharge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充值订单数据访问接口
 */
@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {
}