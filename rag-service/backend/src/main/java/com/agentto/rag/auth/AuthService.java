package com.agentto.rag.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentto.rag.common.api.BusinessException;

@Service
public class AuthService {

    private final AdminUserRepository userRepository;
    private final AdminSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AdminUserRepository userRepository, AdminSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public LoginResult login(String username, String password) {
        AdminUser user = userRepository.findByUsernameIgnoreCase(username == null ? "" : username.trim())
                .filter(AdminUser::isEnabled)
                .filter(candidate -> passwordEncoder.matches(password == null ? "" : password,
                        candidate.getPasswordHash()))
                .orElseThrow(() -> new BusinessException("AUTH_FAILED", "用户名或密码错误", HttpStatus.UNAUTHORIZED));

        String token = newToken();
        Instant expiresAt = Instant.now().plus(Duration.ofHours(Math.max(properties.sessionHours(), 1)));
        sessionRepository.save(AdminSession.create(user, hash(token), expiresAt));
        return new LoginResult(token, expiresAt, AdminProfile.from(user));
    }

    @Transactional
    public AdminPrincipal authenticate(String token) {
        if (token == null || token.isBlank()) {
            throw invalidSession();
        }
        AdminSession session = sessionRepository.findByTokenHash(hash(token)).orElseThrow(this::invalidSession);
        if (session.getExpiresAt().isBefore(Instant.now()) || !session.getUser().isEnabled()) {
            sessionRepository.delete(session);
            throw invalidSession();
        }
        session.touch();
        return AdminPrincipal.from(session.getUser());
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionRepository.deleteByTokenHash(hash(token));
        }
    }

    private BusinessException invalidSession() {
        return new BusinessException("SESSION_INVALID", "登录状态已失效", HttpStatus.UNAUTHORIZED);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
