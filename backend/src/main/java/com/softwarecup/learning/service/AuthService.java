package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwarecup.learning.dto.AuthDtos.FaceEnrollRequest;
import com.softwarecup.learning.dto.AuthDtos.FaceLoginRequest;
import com.softwarecup.learning.dto.AuthDtos.LoginRequest;
import com.softwarecup.learning.dto.AuthDtos.LoginResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private static final int MIN_EMBEDDING_SIZE = 64;
    private static final double FACE_MATCH_THRESHOLD = 0.55;

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final ObjectMapper objectMapper;

    public AuthService(
            JdbcClient jdbc,
            PasswordEncoder passwordEncoder,
            SessionService sessions,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    public LoginResponse login(LoginRequest request) {
        Map<String, Object> user = findUser(request.username());
        if (!passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return createLoginResponse(user);
    }

    public void enrollFace(FaceEnrollRequest request) {
        validateEmbedding(request.embedding());
        Map<String, Object> user = findUser(request.username());
        if (!passwordEncoder.matches(request.password(), (String) user.get("password_hash"))) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        try {
            jdbc.sql("UPDATE users SET face_embedding = :embedding WHERE id = :id")
                    .param("embedding", objectMapper.writeValueAsString(request.embedding()))
                    .param("id", user.get("id"))
                    .update();
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("人脸特征保存失败");
        }
    }

    public LoginResponse faceLogin(FaceLoginRequest request) {
        validateEmbedding(request.embedding());
        Map<String, Object> user = findUser(request.username());
        Object rawEmbedding = user.get("face_embedding");
        if (rawEmbedding == null || rawEmbedding.toString().isBlank()) {
            throw new IllegalArgumentException("该账号尚未录入人脸，请先使用密码录入");
        }
        try {
            List<Double> enrolled = objectMapper.readValue(
                    rawEmbedding.toString(),
                    new TypeReference<>() {}
            );
            if (similarity(enrolled, request.embedding()) < FACE_MATCH_THRESHOLD) {
                throw new IllegalArgumentException("人脸验证未通过，请重试或使用密码登录");
            }
            return createLoginResponse(user);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("已录入的人脸特征无效，请重新录入");
        }
    }

    private Map<String, Object> findUser(String username) {
        int count = jdbc.sql("""
                SELECT COUNT(*) FROM users
                WHERE username = :username AND status = 'ACTIVE'
                """)
                .param("username", username)
                .query(Integer.class)
                .single();
        if (count == 0) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return jdbc.sql("""
                SELECT id, nickname, password_hash, face_embedding
                FROM users
                WHERE username = :username AND status = 'ACTIVE'
                """)
                .param("username", username)
                .query()
                .singleRow();
    }

    private LoginResponse createLoginResponse(Map<String, Object> user) {
        long userId = ((Number) user.get("id")).longValue();
        return new LoginResponse(sessions.create(userId), userId, (String) user.get("nickname"));
    }

    private void validateEmbedding(List<Double> embedding) {
        if (embedding.size() < MIN_EMBEDDING_SIZE
                || embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("人脸特征无效");
        }
    }

    private double similarity(List<Double> first, List<Double> second) {
        if (first.size() != second.size()) {
            return 0;
        }
        double squaredDifference = 0;
        for (int index = 0; index < first.size(); index++) {
            double difference = first.get(index) - second.get(index);
            squaredDifference += difference * difference;
        }
        double distance = 25 * squaredDifference;
        double normalized = (1 - Math.sqrt(distance) / 100 - 0.2) / 0.6;
        return Math.max(0, Math.min(1, normalized));
    }
}
