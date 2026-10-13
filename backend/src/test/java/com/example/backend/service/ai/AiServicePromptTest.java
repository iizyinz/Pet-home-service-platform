package com.example.backend.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统提示词契约测试：防止换提示词时把安全准则/角色约束弄丢。
 */
class AiServicePromptTest {

    @Test
    @DisplayName("系统提示词包含角色、安全准则与输出规范")
    void systemPromptKeepsRoleAndSafetySections() {
        String prompt = AiService.SYSTEM_PROMPT;

        assertThat(prompt).isNotBlank();
        // 角色与能力
        assertThat(prompt).contains("# Role: 智能宠物护理顾问");
        assertThat(prompt).contains("Core Competencies");
        // 安全准则：非兽医声明 / 拒绝有害建议 / 科学依据
        assertThat(prompt).contains("非兽医声明");
        assertThat(prompt).contains("拒绝有害建议");
        assertThat(prompt).contains("科学依据");
        assertThat(prompt).contains("请遵医嘱");
        // 工作流与输出规范
        assertThat(prompt).contains("Workflow");
        assertThat(prompt).contains("Markdown");
    }

    @Test
    @DisplayName("提示词长度可控，避免挤占上下文窗口")
    void systemPromptLengthIsReasonable() {
        assertThat(AiService.SYSTEM_PROMPT.length()).isBetween(500, 4000);
    }
}
