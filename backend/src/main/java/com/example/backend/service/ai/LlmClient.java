package com.example.backend.service.ai;

import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型客户端（OpenAI 兼容接口，DeepSeek 等），SSE 流式读取。
 * 失败抛 AI_ERROR（3001），由上层兜底且不写入会话历史。
 */
@Slf4j
@Component
public class LlmClient {

    @Value("${app.llm.base-url}")
    private String baseUrl;

    @Value("${app.llm.api-key}")
    private String apiKey;

    @Value("${app.llm.model}")
    private String model;

    @Value("${app.llm.connect-timeout-seconds}")
    private int connectTimeoutSeconds;

    @Value("${app.llm.read-timeout-seconds}")
    private int readTimeoutSeconds;

    private final ObjectMapper mapper = new ObjectMapper();

    public record ChatMsg(String role, String content) {
    }

    /**
     * 流式对话：每个增量通过 onDelta 回调，返回完整回答。
     */
    public String streamChat(List<ChatMsg> messages, Consumer<String> onDelta) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            ArrayNode arr = body.putArray("messages");
            for (ChatMsg msg : messages) {
                ObjectNode m = arr.addObject();
                m.put("role", msg.role());
                m.put("content", msg.content());
            }

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(readTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String err = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.error("LLM 调用失败 status={} body={}", response.statusCode(), err);
                throw new BizException(ErrorCode.AI_ERROR);
            }

            StringBuilder full = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    JsonNode node = mapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta").path("content");
                    if (!delta.isMissingNode() && !delta.isNull() && !delta.asText().isEmpty()) {
                        String token = delta.asText();
                        full.append(token);
                        onDelta.accept(token);
                    }
                }
            }
            if (full.isEmpty()) {
                throw new BizException(ErrorCode.AI_ERROR, "模型返回为空，请稍后重试");
            }
            return full.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM 调用异常: {}", e.getMessage());
            throw new BizException(ErrorCode.AI_ERROR);
        }
    }
}
