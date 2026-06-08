package com.chuamgwei.module.credit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditConsumeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户端消费记录数据访问接口
 */
@Mapper
public interface CreditConsumeRecordMapper extends BaseMapper<CreditConsumeRecord> {

    /**
     * 分页查询用户端消费记录
     */
    Page<CreditConsumeRecord> selectConsumeRecordPage(Page<CreditConsumeRecord> page,
                                                      @Param("userUuid") String userUuid);
}