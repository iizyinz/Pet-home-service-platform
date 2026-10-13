package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.ChatConversation;
import org.apache.ibatis.annotations.Mapper;

/** ChatConversation Mapper */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}

