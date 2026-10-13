package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Banner;
import org.apache.ibatis.annotations.Mapper;

/** Banner Mapper */
@Mapper
public interface BannerMapper extends BaseMapper<Banner> {
}

