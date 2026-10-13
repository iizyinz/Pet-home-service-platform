package com.example.backend.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2 对象存储真实上传验证（默认跳过）。
 * <p>
 * 需要注入环境变量 {@code R2_ACCESS_KEY_ID} 等才会执行，密钥不写入代码仓库：
 * <pre>
 *   R2_ACCOUNT_ID / R2_ENDPOINT / R2_BUCKET / R2_ACCESS_KEY_ID
 *   R2_SECRET_ACCESS_KEY / R2_PUBLIC_BASE_URL
 *   mvnw test -Dtest=R2StorageServiceTest
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "R2_ACCESS_KEY_ID", matches = ".+")
class R2StorageServiceTest {

    private R2StorageService newService() {
        R2StorageService service = new R2StorageService();
        // endpoint 可留空：生产代码会按 R2_ACCOUNT_ID 推导，测试与运行时保持一致
        ReflectionTestUtils.setField(service, "endpoint", envOr("R2_ENDPOINT", ""));
        ReflectionTestUtils.setField(service, "accountId", env("R2_ACCOUNT_ID"));
        ReflectionTestUtils.setField(service, "region", envOr("R2_REGION", "auto"));
        ReflectionTestUtils.setField(service, "bucket", env("R2_BUCKET"));
        ReflectionTestUtils.setField(service, "accessKey", env("R2_ACCESS_KEY_ID"));
        ReflectionTestUtils.setField(service, "secretKey", env("R2_SECRET_ACCESS_KEY"));
        ReflectionTestUtils.setField(service, "publicBaseUrl", env("R2_PUBLIC_BASE_URL"));
        ReflectionTestUtils.setField(service, "keyPrefix", envOr("R2_KEY_PREFIX", "pet"));
        return service;
    }

    private static String env(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + key);
        }
        return v;
    }

    private static String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    /** 1x1 透明 PNG */
    private byte[] tinyPng() {
        byte[] png = new byte[68];
        byte[] head = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(head, 0, png, 0, head.length);
        new Random(42).nextBytes(png); // 覆盖为随机内容，仅用于验证字节可写可读
        return png;
    }

    @Test
    void uploadThenFetch() {
        R2StorageService service = newService();
        String url = service.upload("png", "image/png", tinyPng());
        // 输出脱敏：隐藏公开域名，避免测试报告（surefire-reports）泄漏存储桶访问地址
        System.out.println("[R2] uploaded -> <public-base-url>"
                + url.substring(env("R2_PUBLIC_BASE_URL").length()));

        assertTrue(url.startsWith(env("R2_PUBLIC_BASE_URL") + "/"), "返回 URL 应基于公开访问域名");

        HttpResponse<String> resp;
        try {
            resp = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("访问公开 URL 失败", e);
        }
        System.out.println("[R2] public GET status = " + resp.statusCode());
        assertEquals(200, resp.statusCode(), "公开访问域名应返回 200（需在 Cloudflare 开启 r2.dev 或自定义域）");
    }
}
