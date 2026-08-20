package com.marketpulse.aggregation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Without spring-boot-starter-web/-json, Spring Boot's Jackson
 * auto-configuration doesn't provide an ObjectMapper bean - defined
 * explicitly here rather than pulling in a web starter this service
 * doesn't otherwise need. Jdk8Module lets TrendSummary's Optional fields
 * serialize directly for the Redis cache, no separate cache-only DTO.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(new Jdk8Module());
    }
}
