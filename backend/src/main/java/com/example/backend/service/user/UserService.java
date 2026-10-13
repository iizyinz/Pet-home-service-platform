package com.example.backend.service.user;

import com.example.backend.common.context.LoginUser;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.user.ProfileUpdateRequest;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人信息服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final AuthService authService;

    public SysUser profile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    /** 更新昵称/头像/手机号；携带原密码+新密码时修改密码 */
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, ProfileUpdateRequest req) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatar() != null) {
            user.setAvatar(req.getAvatar());
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()) {
            user.setPhone(req.getPhone());
        }
        boolean changePassword = req.getOldPassword() != null && req.getNewPassword() != null;
        if (changePassword) {
            if (!new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                    .matches(req.getOldPassword(), user.getPassword())) {
                throw new BizException(ErrorCode.OLD_PASSWORD_WRONG);
            }
            if (req.getNewPassword().length() < 6) {
                throw new BizException(ErrorCode.BAD_REQUEST, "新密码长度不能小于 6 位");
            }
            user.setPassword(authService.encode(req.getNewPassword()));
        }
        userMapper.updateById(user);
    }

    /** 手机号脱敏展示 138****5678 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    public LoginUser currentUser() {
        return com.example.backend.common.context.UserContext.require();
    }
}
