package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

/** ChatMessage Mapper */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

