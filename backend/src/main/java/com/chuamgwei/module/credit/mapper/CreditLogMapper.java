package com.chuamgwei.module.credit.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.module.credit.entity.CreditLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户端统一积分日志数据访问接口
 */
@Mapper
public interface CreditLogMapper {

    /**
     * 分页查询当前用户统一积分日志
     */
    Page<CreditLogVO> selectUserLogPage(Page<CreditLogVO> page,
                                        @Param("userUuid") String userUuid,
                                        @Param("type") String type,
                                        @Param("direction") String direction,
                                        @Param("keyword") String keyword,
                                        @Param("startTime") String startTime,
                                        @Param("endTime") String endTime);
}