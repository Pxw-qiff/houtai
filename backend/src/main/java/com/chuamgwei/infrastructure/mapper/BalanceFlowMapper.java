package com.chuamgwei.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.infrastructure.entity.BalanceFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 钱包余额流水数据访问接口
 */
@Mapper
public interface BalanceFlowMapper extends BaseMapper<BalanceFlow> {
}
