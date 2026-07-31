package com.agentto.rag.ingestion;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.agentto.rag.ingestion.chunk.StructureAwareChunker;

@Configuration
public class IngestionConfiguration {

    @Bean
    StructureAwareChunker structureAwareChunker(ChunkingProperties properties) {
        return new StructureAwareChunker(properties.targetChars(), properties.maxChars(), properties.overlapChars());
    }

    @Bean("ragTaskExecutor")
    ThreadPoolTaskExecutor ragTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("rag-ingestion-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
