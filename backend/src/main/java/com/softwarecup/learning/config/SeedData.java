package com.softwarecup.learning.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

@Component
public class SeedData implements CommandLineRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public SeedData(JdbcClient jdbc, PasswordEncoder passwordEncoder, DataSource dataSource) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) throws Exception {
        ensureFaceEmbeddingColumn();

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

    private void ensureFaceEmbeddingColumn() throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     connection.getCatalog(), null, "%", "%"
             )) {
            while (columns.next()) {
                if ("users".equalsIgnoreCase(columns.getString("TABLE_NAME"))
                        && "face_embedding".equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return;
                }
            }
        }
        jdbc.sql("ALTER TABLE users ADD COLUMN face_embedding LONGTEXT").update();
    }
}

