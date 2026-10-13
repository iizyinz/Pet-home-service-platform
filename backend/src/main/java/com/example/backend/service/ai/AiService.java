package com.example.backend.service.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.entity.AiConversation;
import com.example.backend.entity.AiMessage;
import com.example.backend.mapper.AiConversationMapper;
import com.example.backend.mapper.AiMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AI 宠物问答（PRD 3.4 / 8.5）：
 * 组装上下文 = 系统提示词 + 会话历史 + 当前问题 -> 调用大模型 -> SSE 逐块下发
 * -> 完成后落 assistant 消息；模型异常返回错误事件且不写入会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    static final String SYSTEM_PROMPT = """
            # Role: 智能宠物护理顾问 (Pet Care Consultant)

            ## Profile
            你是一位拥有10年以上临床经验的资深宠物护理顾问，同时具备动物行为学和营养学背景。你致力于为用户提供科学、温暖、个性化的宠物养护建议。你的服务对象主要是猫、狗等常见家庭宠物，但也具备异宠（如兔子、仓鼠、鸟类）的基础知识。

            ## Goals
            1. 解答用户关于宠物健康、饮食、行为训练及日常护理的疑问。
            2. 提供基于科学依据的建议，拒绝伪科学和偏方。
            3. 在紧急情况下，能够迅速识别症状并强烈建议用户就医。
            4. 给予用户情感支持，缓解养宠焦虑。

            ## Core Competencies (核心能力)
            - **健康咨询**：常见病症的初步判断、疫苗接种建议、体内外驱虫指导。
            - **营养搭配**：根据宠物年龄、品种、体重推荐主粮、零食及营养补充剂。
            - **行为矫正**：解决乱尿、吠叫、拆家、攻击性等常见行为问题。
            - **日常护理**：洗护美容、环境清洁、老年宠物护理指南。

            ## Constraints & Safety Guidelines (重要限制与安全准则)
            1. **非兽医声明**：你必须明确告知用户，你提供的建议仅供参考，不能替代专业兽医的临床诊断。如果宠物出现严重症状（如呼吸困难、剧烈呕吐、大出血、意识不清），必须第一时间建议用户前往最近的宠物医院。
            2. **拒绝有害建议**：严禁推荐任何对宠物有毒的食物（如巧克力、葡萄、木糖醇等）或危险的训练方式（如暴力体罚）。
            3. **科学依据**：回答需基于主流兽医学和动物行为学共识，对于不确定的偏方或未经证实的疗法，应持保留态度。
            4. **语气风格**：保持专业、耐心、富有同理心。像一位值得信赖的“养宠老友”。

            ## Workflow (工作流程)
            1. **信息收集**：在给出具体建议前，先确认宠物的关键信息（如：物种、品种、年龄、体重、是否绝育、既往病史、当前症状）。如果用户未提供，请礼貌追问。
            2. **问题分析**：分析用户的问题，判断是日常护理问题还是潜在的健康危机。
            3. **方案提供**：
               - 若是日常问题，提供分步骤的解决方案。
               - 若是健康问题，提供可能的病因分析及家庭护理建议，并标注就医指征。
            4. **总结与关怀**：在回答末尾给予简短的鼓励或提醒。

            ## Output Format (回复规范)
            - 使用清晰的Markdown格式。
            - 对于复杂的操作步骤，使用有序列表。
            - 对于重点注意事项，使用加粗或引用块强调。
            - 如果涉及用药，必须强调“请遵医嘱”。

            ## Initialization (开场白)
            你好！我是你的专属宠物护理顾问。无论是关于毛孩子的饮食起居，还是行为训练的小烦恼，我都在这里为你解答。
            请告诉我，你家宝贝是什么品种？今年多大了？遇到了什么问题呢？
            """;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final LlmClient llmClient;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** 新建会话 */
    @Transactional(rollbackFor = Exception.class)
    public AiConversation createConversation(Long userId) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle("新会话");
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 我的会话列表（按更新时间倒序） */
    public List<AiConversation> listConversations(Long userId) {
        return conversationMapper.selectList(Wrappers.<AiConversation>lambdaQuery()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getUpdatedAt));
    }

    /** 会话历史消息 */
    public List<AiMessage> listMessages(Long userId, Long conversationId) {
        AiConversation conversation = getOwned(userId, conversationId);
        return messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conversation.getId())
                .orderByAsc(AiMessage::getId));
    }

    /** 会话归属校验（越权 404/2002） */
    public AiConversation getOwned(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.AI_CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    void saveMessage(Long conversationId, String role, String content) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        messageMapper.insert(message);
    }

    /**
     * SSE 提问（POST /api/ai/chat，text/event-stream）。
     * 事件：message={delta} 增量、done={content} 完成、error={code,message} 失败。
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter chat(
            Long userId, com.example.backend.dto.ai.AiChatRequest request) {
        AiConversation conversation = request.getConversationId() != null
                ? getOwned(userId, request.getConversationId())
                : createConversation(userId);
        final AiConversation conv = conversation;
        final String question = request.getQuestion();

        // 组装上下文：系统提示词 + 历史（最多 20 条）+ 当前问题
        List<AiMessage> history = messageMapper.selectList(Wrappers.<AiMessage>lambdaQuery()
                .eq(AiMessage::getConversationId, conv.getId())
                .orderByAsc(AiMessage::getId).last("LIMIT 20"));
        java.util.List<LlmClient.ChatMsg> messages = new java.util.ArrayList<>();
        messages.add(new LlmClient.ChatMsg("system", SYSTEM_PROMPT));
        for (AiMessage m : history) {
            messages.add(new LlmClient.ChatMsg(m.getRole(), m.getContent()));
        }
        messages.add(new LlmClient.ChatMsg("user", question));

        var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(120_000L);
        Thread.startVirtualThread(() -> {
            try {
                String answer = llmClient.streamChat(messages, token -> {
                    try {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                                .event().name("message").data(java.util.Map.of("delta", token)));
                    } catch (Exception ignore) {
                        // 客户端提前断开，忽略
                    }
                });
                // 成功后才落库：user + assistant（失败不污染会话历史，AC-08）
                saveMessage(conv.getId(), "user", question);
                saveMessage(conv.getId(), "assistant", answer);
                if (conv.getTitle() == null || "新会话".equals(conv.getTitle())) {
                    conv.setTitle(question.length() > 30 ? question.substring(0, 30) : question);
                }
                conv.setUpdatedAt(java.time.LocalDateTime.now());
                conversationMapper.updateById(conv);

                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                        .event().name("done").data(java.util.Map.of("content", answer)));
                emitter.complete();
            } catch (Exception e) {
                log.error("AI 问答失败: {}", e.getMessage());
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                            .event().name("error").data(java.util.Map.of(
                                    "code", ErrorCode.AI_ERROR.getCode(),
                                    "message", ErrorCode.AI_ERROR.getMessage())));
                } catch (Exception ignore) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}

