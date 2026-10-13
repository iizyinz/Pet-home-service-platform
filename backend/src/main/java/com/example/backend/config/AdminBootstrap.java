package com.example.backend.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 系统预置：管理端账号由系统预置（PRD 5.1）。
 * <p>
 * 初始管理员口令不再内置默认值，必须通过环境变量 {@code ADMIN_INIT_PASSWORD} 注入；
 * 未注入时启动直接失败，避免出现弱口令账号。
 * 创建成功后日志不会打印口令，且建议登录后立即修改。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final SysUserMapper userMapper;

    @Override
    public void run(String... args) {
        Long adminCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getRole, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            return;
        }
        String initialPassword = System.getenv("ADMIN_INIT_PASSWORD");
        if (initialPassword == null || initialPassword.isBlank()) {
            throw new IllegalStateException(
                    "ADMIN_INIT_PASSWORD 未设置：首次启动需要通过环境变量提供管理员初始口令（长度 8-32 位），"
                            + "创建成功后请立即登录并修改密码。");
        }
        if (initialPassword.length() < 8 || initialPassword.length() > 32) {
            throw new IllegalStateException("ADMIN_INIT_PASSWORD 长度必须在 8-32 位之间。");
        }
        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(new BCryptPasswordEncoder().encode(initialPassword));
        admin.setNickname("平台管理员");
        admin.setRole("ADMIN");
        admin.setBalance(java.math.BigDecimal.ZERO);
        admin.setStatus(1);
        userMapper.insert(admin);
        log.info("已初始化管理员账号 admin（口令由环境变量 ADMIN_INIT_PASSWORD 注入，日志不记录口令，请登录后立即修改）");
    }
}
