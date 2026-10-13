package com.example.backend.service.storage;

import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * Cloudflare R2 对象存储（S3 兼容 API）。
 * <p>
 * 图片等静态资源不再落本地磁盘，统一上传到 R2 Bucket，并通过公开访问域名
 * （R2.dev 子域或自定义域）对外提供访问。
 * <p>
 * 配置项（{@code app.r2.*}）全部支持环境变量覆盖，密钥不要写进代码仓库：
 * <ul>
 *   <li>{@code R2_ACCOUNT_ID} / {@code R2_ENDPOINT}</li>
 *   <li>{@code R2_ACCESS_KEY_ID} / {@code R2_SECRET_ACCESS_KEY}</li>
 *   <li>{@code R2_BUCKET} / {@code R2_PUBLIC_BASE_URL}</li>
 * </ul>
 */
@Slf4j
@Service
public class R2StorageService {

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** R2 接入点，留空则由 Account ID 推导：https://{accountId}.r2.cloudflarestorage.com */
    @Value("${app.r2.endpoint:}")
    private String endpoint;

    @Value("${app.r2.account-id:}")
    private String accountId;

    @Value("${app.r2.region:auto}")
    private String region;

    @Value("${app.r2.bucket:}")
    private String bucket;

    @Value("${app.r2.access-key:}")
    private String accessKey;

    @Value("${app.r2.secret-key:}")
    private String secretKey;

    /** 公开访问基址（不含末尾斜杠），如 https://pub-xxx.r2.dev 或 https://img.example.com */
    @Value("${app.r2.public-base-url:}")
    private String publicBaseUrl;

    /** 对象 Key 前缀，便于按业务分目录 */
    @Value("${app.r2.key-prefix:pet}")
    private String keyPrefix;

    private volatile S3Client s3Client;

    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            log.warn("R2 对象存储未配置（缺少 R2_ACCOUNT_ID/R2_ENDPOINT、R2_BUCKET、R2_ACCESS_KEY_ID、"
                    + "R2_SECRET_ACCESS_KEY 或 R2_PUBLIC_BASE_URL），图片上传将不可用");
            return;
        }
        // 只输出 bucket，不打印 endpoint（endpoint 含 Cloudflare Account ID，属敏感信息）
        log.info("R2 对象存储已就绪：bucket={}（endpoint 已按隐私要求隐藏）", bucket);
    }

    /**
     * 上传图片到 R2。
     *
     * @param ext         小写扩展名，不含点（jpg/png/gif/webp）
     * @param contentType MIME 类型
     * @param content     文件字节内容
     * @return 可直接访问的完整 URL
     */
    public String upload(String ext, String contentType, byte[] content) {
        if (!isConfigured()) {
            throw new BizException(ErrorCode.UPLOAD_FAILED, "对象存储未配置，请先设置 R2 环境变量");
        }
        String key = buildKey(ext);
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();
            client().putObject(request, RequestBody.fromBytes(content));
            String url = publicBaseUrl + "/" + key;
            // 只记录对象 Key，不打印完整公开域名（避免日志泄漏存储桶访问地址）
            log.info("R2 上传成功：key={} ({} bytes)", key, content.length);
            return url;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("R2 上传失败：key={}", key, e);
            throw new BizException(ErrorCode.UPLOAD_FAILED, "对象存储上传失败：" + e.getMessage());
        }
    }

    /** 生成对象 Key：{prefix}/{yyyy/MM/dd}/{uuid}.{ext} */
    private String buildKey(String ext) {
        String prefix = keyPrefix == null ? "" : keyPrefix.trim();
        prefix = prefix.replaceAll("^/+|/+$", "");
        String datePath = LocalDate.now().format(KEY_DATE);
        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        return prefix.isEmpty() ? datePath + "/" + filename : prefix + "/" + datePath + "/" + filename;
    }

    private boolean isConfigured() {
        boolean hasEndpoint = !isBlank(endpoint) || !isBlank(accountId);
        return hasEndpoint && !isBlank(bucket) && !isBlank(accessKey)
                && !isBlank(secretKey) && !isBlank(publicBaseUrl);
    }

    private String resolveEndpoint() {
        if (!isBlank(endpoint)) {
            return endpoint;
        }
        return "https://" + accountId + ".r2.cloudflarestorage.com";
    }

    private synchronized S3Client client() {
        if (s3Client == null) {
            s3Client = S3Client.builder()
                    .endpointOverride(URI.create(resolveEndpoint()))
                    .region(Region.of(isBlank(region) ? "auto" : region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    // R2 使用虚拟主机/路径样式均可，显式开启路径样式以兼容自定义 Endpoint
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        }
        return s3Client;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 依据扩展名推断 MIME 类型（供上传方使用） */
    public static String contentTypeOf(String ext) {
        return switch (ext == null ? "" : ext.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
