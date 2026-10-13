package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.service.storage.R2StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传（Cloudflare R2 对象存储，PRD 8.13 上传做大小类型校验）
 */
@Tag(name = "文件上传", description = "图片上传（聊天/进度/资质/宠物头像通用），存储于 R2 对象存储")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private static final long MAX_SIZE = 5 * 1024 * 1024;

    private final R2StorageService storageService;

    @Operation(summary = "上传图片", description = "multipart/form-data，字段名 file；仅图片且 ≤5MB，返回 R2 公开访问 URL")
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.FILE_INVALID, "文件不能超过 5MB");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.FILE_INVALID, "仅支持 jpg/jpeg/png/gif/webp");
        }
        try {
            byte[] content = file.getBytes();
            String url = storageService.upload(ext, R2StorageService.contentTypeOf(ext), content);
            return Result.ok(Map.of("url", url));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.UPLOAD_FAILED);
        }
    }
}
