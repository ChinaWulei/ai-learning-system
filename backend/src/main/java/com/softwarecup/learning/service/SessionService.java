package com.softwarecup.learning.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {
    private final JdbcClient jdbc;

    public SessionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public String create(long userId) {
        String token = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO auth_session(token, user_id, expires_at)
                VALUES (:token, :userId, :expiresAt)
                """)
                .param("token", token)
                .param("userId", userId)
                .param("expiresAt", LocalDateTime.now().plusDays(7))
                .update();
        return token;
    }

    public Optional<Long> resolve(String token) {
        return jdbc.sql("""
                SELECT user_id FROM auth_session
                WHERE token = :token AND expires_at > CURRENT_TIMESTAMP
                """)
                .param("token", token)
                .query(Long.class)
                .optional();
    }
}
