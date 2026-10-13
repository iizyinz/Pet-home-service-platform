package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.StaffProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** StaffProfile Mapper */
@Mapper
public interface StaffProfileMapper extends BaseMapper<StaffProfile> {

    @Select("SELECT COUNT(*) FROM staff_profile")
    long countAll();
}