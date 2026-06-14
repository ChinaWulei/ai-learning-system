package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProfileService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public ProfileService(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> get(long userId) {
        return jdbc.sql("SELECT * FROM user_profile WHERE user_id = :userId")
                .param("userId", userId)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "level", rs.getString("level_name"),
                        "mastery", readMap(rs.getString("mastery_json")),
                        "weak_points", readList(rs.getString("weakness_json")),
                        "preferences", readList(rs.getString("preference_json")),
                        "learning_history", readList(rs.getString("history_json"))
                ))
                .optional()
                .orElse(Map.of(
                        "level", "beginner",
                        "mastery", Map.of(),
                        "weak_points", List.of(),
                        "preferences", List.of(),
                        "learning_history", List.of()
                ));
    }

    public void save(long userId, Map<String, Object> profile) {
        int updated = jdbc.sql("""
                UPDATE user_profile SET level_name = :level, mastery_json = :mastery,
                    weakness_json = :weakness, preference_json = :preference,
                    history_json = :history, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = :userId
                """)
                .param("userId", userId)
                .param("level", profile.getOrDefault("level", "beginner"))
                .param("mastery", write(profile.getOrDefault("mastery", Map.of())))
                .param("weakness", write(profile.getOrDefault("weak_points", List.of())))
                .param("preference", write(profile.getOrDefault("preferences", List.of())))
                .param("history", write(profile.getOrDefault("learning_history", List.of())))
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO user_profile(user_id, level_name, mastery_json, weakness_json,
                        preference_json, history_json)
                    VALUES (:userId, :level, :mastery, :weakness, :preference, :history)
                    """)
                    .param("userId", userId)
                    .param("level", profile.getOrDefault("level", "beginner"))
                    .param("mastery", write(profile.getOrDefault("mastery", Map.of())))
                    .param("weakness", write(profile.getOrDefault("weak_points", List.of())))
                    .param("preference", write(profile.getOrDefault("preferences", List.of())))
                    .param("history", write(profile.getOrDefault("learning_history", List.of())))
                    .update();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("画像JSON损坏", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> readList(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return json.readValue(value, List.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("画像JSON损坏", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化JSON", exception);
        }
    }
}
