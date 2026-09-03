package com.agentto.rag.asset;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class ContentAssetConfiguration {

    @Bean
    Clock modelGovernanceClock() {
        return Clock.system(ZoneOffset.UTC);
    }

    @Bean
    TransactionTemplate assetTransactionTemplate(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRED);
        return template;
    }
}
