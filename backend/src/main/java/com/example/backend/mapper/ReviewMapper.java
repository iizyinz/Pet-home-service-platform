package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Review Mapper */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 服务师显示评价的平均星级（无评价返回 0） */
    @Select("SELECT COALESCE(AVG(rating), 0) FROM review WHERE staff_id = #{staffId} AND status = 1")
    java.math.BigDecimal avgRating(@Param("staffId") Long staffId);
}