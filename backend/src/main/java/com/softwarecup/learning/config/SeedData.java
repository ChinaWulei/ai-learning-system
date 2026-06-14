package com.softwarecup.learning.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedData implements CommandLineRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public SeedData(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        int count = jdbc.sql("SELECT COUNT(*) FROM users WHERE username = :username")
                .param("username", "demo")
                .query(Integer.class)
                .single();
        if (count == 0) {
            jdbc.sql("""
                    INSERT INTO users(username, password_hash, nickname)
                    VALUES (:username, :password, :nickname)
                    """)
                    .param("username", "demo")
                    .param("password", passwordEncoder.encode("demo123"))
                    .param("nickname", "演示学生")
                    .update();
        }
    }
}

