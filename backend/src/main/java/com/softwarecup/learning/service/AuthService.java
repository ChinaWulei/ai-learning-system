package com.softwarecup.learning.service;

import com.softwarecup.learning.dto.AuthDtos.LoginRequest;
import com.softwarecup.learning.dto.AuthDtos.LoginResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuthService {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;

    public AuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder, SessionService sessions) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    public LoginResponse login(LoginRequest request) {
        int count = jdbc.sql("SELECT COUNT(*) FROM users WHERE username = :username")
                .param("username", request.username())
                .query(Integer.class)
                .single();
        if (count == 0) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        Map<String, Object> user = jdbc.sql("""
                SELECT id, nickname, password_hash FROM users WHERE username = :username
                """)
                .param("username", request.username())
                .query()
                .singleRow();
        if (!passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        long userId = ((Number) user.get("id")).longValue();
        return new LoginResponse(sessions.create(userId), userId, (String) user.get("nickname"));
    }
}
