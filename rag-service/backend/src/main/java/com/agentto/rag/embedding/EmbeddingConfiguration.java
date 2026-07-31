package com.agentto.rag.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.agentto.rag.embedding.TeiEmbeddingClient.ElasticsearchDimensions;

@Configuration
public class EmbeddingConfiguration {

    @Bean
    ElasticsearchDimensions elasticsearchDimensions(
            @Value("${rag.elasticsearch.dimensions:1024}") int dimensions) {
        return new ElasticsearchDimensions(dimensions);
    }
}
