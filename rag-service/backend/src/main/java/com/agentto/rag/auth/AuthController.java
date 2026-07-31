package com.agentto.rag.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentto.rag.common.api.ApiResponse;
import com.agentto.rag.common.api.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ApiResponse.ok(authService.login(request.username(), request.password()),
                TraceIdFilter.current(servletRequest));
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(AuthRequestContext.bearerToken(request));
        return ApiResponse.ok(null, TraceIdFilter.current(request));
    }

    @GetMapping("/me")
    ApiResponse<AdminPrincipal> me(HttpServletRequest request) {
        return ApiResponse.ok(AuthRequestContext.principal(request), TraceIdFilter.current(request));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
