package com.agentto.rag.auth;

public record AdminProfile(Long id, String username, String displayName) {

    static AdminProfile from(AdminUser user) {
        return new AdminProfile(user.getId(), user.getUsername(), user.getDisplayName());
    }
}
