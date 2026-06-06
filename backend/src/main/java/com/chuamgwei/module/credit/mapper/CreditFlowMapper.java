package com.chuamgwei.module.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chuamgwei.module.credit.entity.CreditFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分流水数据访问接口
 */
@Mapper
public interface CreditFlowMapper extends BaseMapper<CreditFlow> {
}