package com.example.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 宠物上门服务小程序后端启动类
 * <p>
 * 分层：controller -> service(状态机/事务) -> mapper(MyBatis-Plus) -> MySQL
 * 侧路：WebSocket(聊天推送) + Schedule(订单超时/自动确认) + AOP(审计)
 */
@SpringBootApplication
@MapperScan("com.example.backend.mapper")
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
