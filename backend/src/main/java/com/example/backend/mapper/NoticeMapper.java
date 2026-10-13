package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Notice;
import org.apache.ibatis.annotations.Mapper;

/** Notice Mapper */
@Mapper
public interface NoticeMapper extends BaseMapper<Notice> {
}

