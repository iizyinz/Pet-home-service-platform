package com.example.backend.common.audit;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 审计切面：拦截 @AuditLog 方法，记录操作人/动作/对象/详情/结果/IP（委托 AuditService）
 */
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditService auditService;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint point, AuditLog auditLog) throws Throwable {
        Object[] args = point.getArgs();
        String detail = args.length == 0 ? "" : Arrays.toString(args);
        try {
            Object result = point.proceed();
            auditService.record(auditLog.action(), auditLog.target(), detail);
            return result;
        } catch (Throwable t) {
            auditService.recordFail(auditLog.action(), auditLog.target(), detail, t.getMessage());
            throw t;
        }
    }
}
