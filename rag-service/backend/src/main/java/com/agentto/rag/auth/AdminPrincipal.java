package com.agentto.rag.auth;

public record AdminPrincipal(Long userId, String username, String displayName) {

    static AdminPrincipal from(AdminUser user) {
        return new AdminPrincipal(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
