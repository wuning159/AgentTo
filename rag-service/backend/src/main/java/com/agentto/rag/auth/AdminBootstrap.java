package com.agentto.rag.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final AuthProperties properties;
    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AuthProperties properties, AdminUserRepository repository, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrapEnabled()) {
            return;
        }
        String username = properties.bootstrapUsername();
        String password = properties.bootstrapPassword();
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            throw new IllegalStateException("RAG 管理员初始化账号或密码未正确配置");
        }
        if (!repository.existsByUsernameIgnoreCase(username)) {
            repository.save(AdminUser.create(username.trim(), "RAG 技术管理员", passwordEncoder.encode(password)));
        }
    }
}
