package com.agentto.rag.auth;

import java.time.Instant;

public record LoginResult(String token, Instant expiresAt, AdminProfile profile) {
}
