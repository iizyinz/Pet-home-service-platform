package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Announcement;
import org.apache.ibatis.annotations.Mapper;

/** Announcement Mapper */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
}

