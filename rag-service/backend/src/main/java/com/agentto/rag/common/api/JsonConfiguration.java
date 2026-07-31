package com.agentto.rag.common.api;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

@Configuration
public class JsonConfiguration {

    @Bean
    ObjectMapper legacyObjectMapper() {
        return JsonMapper.builder()
                .build();
    }
}
