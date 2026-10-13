package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.ServiceOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** ServiceOrder Mapper */
@Mapper
public interface ServiceOrderMapper extends BaseMapper<ServiceOrder> {

    @Select("SELECT COUNT(*) FROM service_order")
    long countAll();

    @Select("SELECT COUNT(*) FROM service_order WHERE created_at >= CURDATE()")
    long countToday();

    @Select("SELECT COALESCE(SUM(amount),0) FROM service_order WHERE status = 'COMPLETED'")
    java.math.BigDecimal sumCompletedRevenue();

    @Select("SELECT COALESCE(SUM(amount),0) FROM service_order WHERE status = 'COMPLETED' AND confirm_time >= CURDATE()")
    java.math.BigDecimal sumCompletedRevenueToday();
}