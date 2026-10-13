package com.example.backend.service.chat;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.chat.OnlineSessionManager;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.entity.ChatConversation;
import com.example.backend.entity.ChatMessage;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.ChatConversationMapper;
import com.example.backend.mapper.ChatMessageMapper;
import com.example.backend.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 即时聊天（PRD 8.4）：消息先落库 -> 判断接收方在线 -> WebSocket 定向推送；
 * 离线留存供拉取；会话列表聚合最新一条与未读数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final OnlineSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    /** 发送消息：仅用户/服务师之间私聊（禁止与管理员或自己聊天） */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessage send(Long senderId, Long receiverId, String content, String msgType) {
        if (senderId.equals(receiverId)) {
            throw new BizException(ErrorCode.CHAT_FORBIDDEN, "不能与自己私聊");
        }
        SysUser receiver = userMapper.selectById(receiverId);
        SysUser sender = userMapper.selectById(senderId);
        if (receiver == null || sender == null
                || "ADMIN".equals(receiver.getRole()) || "ADMIN".equals(sender.getRole())) {
            throw new BizException(ErrorCode.CHAT_FORBIDDEN);
        }
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息内容不能为空");
        }

        ChatConversation conversation = getOrCreateConversation(senderId, receiverId);
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversation.getId());
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setContent(content);
        message.setMsgType(msgType == null || msgType.isBlank() ? "TEXT" : msgType);
        message.setIsRead(0);
        messageMapper.insert(message);

        ChatConversation upd = new ChatConversation();
        upd.setId(conversation.getId());
        upd.setLastMessage(content.length() > 60 ? content.substring(0, 60) : content);
        upd.setLastMessageAt(LocalDateTime.now());
        conversationMapper.updateById(upd);

        if (sessionManager.isOnline(receiverId)) {
            try {
                sessionManager.sendTo(receiverId,
                        "{\"type\":\"chat\",\"data\":" + objectMapper.writeValueAsString(message) + "}");
            } catch (Exception e) {
                log.warn("聊天推送失败 {}", e.getMessage());
            }
        }
        return message;
    }

    /** 会话唯一（参与者对，小 ID 在前） */
    public ChatConversation getOrCreateConversation(Long a, Long b) {
        long min = Math.min(a, b), max = Math.max(a, b);
        ChatConversation exist = conversationMapper.selectOne(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserAId, min).eq(ChatConversation::getUserBId, max));
        if (exist != null) {
            return exist;
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setUserAId(min);
        conversation.setUserBId(max);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 会话列表：最新一条 + 未读数 + 对方信息 */
    public List<Map<String, Object>> conversationList(Long userId) {
        List<ChatConversation> list = conversationMapper.selectList(Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserAId, userId).or().eq(ChatConversation::getUserBId, userId)
                .orderByDesc(ChatConversation::getLastMessageAt));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatConversation conv : list) {
            Long otherId = conv.getUserAId().equals(userId) ? conv.getUserBId() : conv.getUserAId();
            Long unread = messageMapper.selectCount(Wrappers.<ChatMessage>lambdaQuery()
                    .eq(ChatMessage::getConversationId, conv.getId())
                    .eq(ChatMessage::getReceiverId, userId)
                    .eq(ChatMessage::getIsRead, 0));
            SysUser other = userMapper.selectById(otherId);
            Map<String, Object> item = new HashMap<>();
            item.put("conversationId", conv.getId());
            item.put("peerId", otherId);
            item.put("peerNickname", other == null ? null : other.getNickname());
            item.put("peerAvatar", other == null ? null : other.getAvatar());
            item.put("peerRole", other == null ? null : other.getRole());
            item.put("lastMessage", conv.getLastMessage());
            item.put("lastMessageAt", conv.getLastMessageAt());
            item.put("unreadCount", unread == null ? 0 : unread);
            item.put("online", sessionManager.isOnline(otherId));
            result.add(item);
        }
        return result;
    }

    /** 历史消息分页；拉取即把发给我的消息标记已读（未读归零） */
    @Transactional(rollbackFor = Exception.class)
    public PageResult<ChatMessage> pageMessages(Long userId, Long conversationId, int page, int size) {
        ChatConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null) {
            throw new BizException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        if (!conv.getUserAId().equals(userId) && !conv.getUserBId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        IPage<ChatMessage> result = messageMapper.selectPage(new Page<>(page, size),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByAsc(ChatMessage::getId));

        ChatMessage upd = new ChatMessage();
        upd.setIsRead(1);
        messageMapper.update(upd, Wrappers.<ChatMessage>lambdaUpdate()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getReceiverId, userId)
                .eq(ChatMessage::getIsRead, 0));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 联系人信息（私信入口） */
    public Map<String, Object> peerInfo(Long userId, Long peerId) {
        SysUser peer = userMapper.selectById(peerId);
        if (peer == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        Map<String, Object> info = new HashMap<>();
        info.put("id", peer.getId());
        info.put("nickname", peer.getNickname());
        info.put("avatar", peer.getAvatar());
        info.put("role", peer.getRole());
        info.put("phone", com.example.backend.service.user.UserService.maskPhone(peer.getPhone()));
        info.put("online", sessionManager.isOnline(peerId));
        return info;
    }
}

