package com.example.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局时间格式：yyyy-MM-dd HH:mm:ss（GMT+8，PRD 约定）
 */
@Configuration
public class JacksonConfig {

    public static final DateTimeFormatter PATTERN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper mapper = builder.build();
        mapper.findAndRegisterModules();
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule()
                .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(PATTERN))
                .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(PATTERN)));
        return mapper;
    }
}
