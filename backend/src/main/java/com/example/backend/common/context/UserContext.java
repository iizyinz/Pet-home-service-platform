package com.example.backend.common.context;

import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;

/**
 * 登录上下文（ThreadLocal），由 AuthInterceptor 填充、请求结束清理。
 * 数据类接口必须通过 requireUserId() 附加归属过滤，禁止仅凭前端传参放行。
 */
public final class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录者，未登录抛 401 */
    public static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    /** 当前登录者ID */
    public static Long requireUserId() {
        return require().getId();
    }

    /** 当前登录者必须为管理员，否则抛 403 */
    public static LoginUser requireAdmin() {
        LoginUser user = require();
        if (!user.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    /** 当前登录者必须为服务人员 */
    public static LoginUser requireStaff() {
        LoginUser user = require();
        if (!user.isStaff()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return user;
    }
}
