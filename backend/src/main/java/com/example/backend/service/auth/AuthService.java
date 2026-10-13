package com.example.backend.service.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.context.LoginUser;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.auth.LoginRequest;
import com.example.backend.dto.auth.LoginResponse;
import com.example.backend.dto.auth.RegisterRequest;
import com.example.backend.entity.StaffProfile;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.StaffProfileMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务：验证码登录 / 注册（USER、STAFF）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final StaffProfileMapper staffProfileMapper;
    private final CaptchaService captchaService;
    private final JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${app.jwt.expire-hours}")
    private long expireHours;

    /** 登录：验证码 -> 账号密码 -> 状态 -> 签发 JWT */
    public LoginResponse login(LoginRequest req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());

        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (user == null) {
            throw new BizException(ErrorCode.PASSWORD_WRONG);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.PASSWORD_WRONG);
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ErrorCode.USER_FROZEN);
        }

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRole());
        String token = jwtUtil.generate(loginUser);
        long expiresAt = System.currentTimeMillis() + expireHours * 3600_000L;
        return LoginResponse.of(token, expiresAt, user);
    }

    /** 注册：新用户默认分配 USER/STAFF；STAFF 同步创建待审核档案 */
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest req) {
        if (req.getCaptchaKey() != null && req.getCaptchaCode() != null) {
            captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());
        }
        Long exists = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        user.setPhone(req.getPhone());
        user.setRole(req.getRole());
        user.setBalance(java.math.BigDecimal.ZERO);
        user.setStatus(1);
        userMapper.insert(user);

        if ("STAFF".equals(req.getRole())) {
            StaffProfile profile = new StaffProfile();
            profile.setUserId(user.getId());
            profile.setCertStatus(0);   // 0 待审核
            profile.setExperienceYears(0);
            profile.setAvgRating(java.math.BigDecimal.ZERO);
            profile.setCompletedCount(0);
            profile.setStatus(1);
            staffProfileMapper.insert(profile);
        }
    }

    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
