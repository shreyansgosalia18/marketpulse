package com.marketpulse.aggregation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketPulseOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MarketPulse Aggregation Service API")
                        .description("Per-ticker trend summaries and price history, computed from Kafka-ingested "
                                + "price/sentiment events and served from PostgreSQL (Redis-cached).")
                        .version("v1"));
    }
}
