package com.agentto.rag.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.agentto.rag.common.api.BusinessException;

@ActiveProfiles("test")
@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminUserRepository userRepository;

    @Autowired
    private AdminSessionRepository sessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(AdminUser.create("ragadmin", "RAG 技术管理员",
                passwordEncoder.encode("safe-password")));
    }

    @Test
    void logsInAndAuthenticatesOpaqueSessionToken() {
        LoginResult result = authService.login("ragadmin", "safe-password");

        assertThat(result.token()).isNotBlank();
        assertThat(result.profile().username()).isEqualTo("ragadmin");
        assertThat(authService.authenticate(result.token()).displayName()).isEqualTo("RAG 技术管理员");
        assertThat(sessionRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsWrongPasswordWithoutCreatingSession() {
        assertThatThrownBy(() -> authService.login("ragadmin", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名或密码错误");
        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    void logoutInvalidatesSessionImmediately() {
        LoginResult result = authService.login("ragadmin", "safe-password");

        authService.logout(result.token());

        assertThatThrownBy(() -> authService.authenticate(result.token()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("登录状态已失效");
    }
}
