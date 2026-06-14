package com.softwarecup.learning.controller;

import com.softwarecup.learning.common.ApiResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final JdbcClient jdbc;

    public UserController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get(Authentication authentication) {
        return ApiResponse.ok(jdbc.sql("""
                SELECT id, username, nickname, status, created_at FROM users WHERE id = :id
                """)
                .param("id", userId(authentication))
                .query()
                .singleRow());
    }

    @PutMapping
    public ApiResponse<Void> update(
            Authentication authentication, @RequestBody Map<String, String> request
    ) {
        String nickname = request.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        jdbc.sql("UPDATE users SET nickname = :nickname WHERE id = :id")
                .param("nickname", nickname)
                .param("id", userId(authentication))
                .update();
        return ApiResponse.ok(null);
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

