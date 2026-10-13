package com.example.backend.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端写操作审计注解：由 AuditLogAspect 记录到 operation_log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 动作，如 order.refund / user.freeze */
    String action();

    /** 对象类型，如 order / user */
    String target() default "";
}
