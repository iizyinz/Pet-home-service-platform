package com.example.backend.common.audit;

import com.example.backend.common.context.LoginUser;
import com.example.backend.common.context.UserContext;
import com.example.backend.entity.OperationLog;
import com.example.backend.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 审计记录服务：管理端关键操作写入 operation_log（失败不影响业务）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditService {

    private final OperationLogMapper operationLogMapper;

    public void record(String action, String target, String detail) {
        doRecord(action, target, detail, "SUCCESS", null);
    }

    public void recordFail(String action, String target, String detail, String error) {
        doRecord(action, target, detail, "FAIL", error);
    }

    private void doRecord(String action, String target, String detail, String result, String error) {
        try {
            OperationLog record = new OperationLog();
            LoginUser user = UserContext.get();
            if (user != null) {
                record.setOperatorId(user.getId());
                record.setOperatorName(user.getUsername());
            }
            record.setAction(action);
            record.setTarget(target);
            String body = detail == null ? "" : detail;
            if (body.length() > 1900) {
                body = body.substring(0, 1900);
            }
            record.setDetail(error == null ? body : (body + " | error=" + error));
            record.setResult(result);
            record.setIp(currentIp());
            operationLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("审计日志写入失败: {}", e.getMessage());
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return null;
        }
    }
}
