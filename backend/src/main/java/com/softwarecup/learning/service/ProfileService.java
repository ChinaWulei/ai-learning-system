package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

    public Map<String, Object> progress(long userId) {
        Map<String, Object> profile = get(userId);
        Map<String, Object> mastery = readObjectMap(profile.get("mastery"));
        List<Map<String, Object>> masteryRanking = mastery.entrySet().stream()
                .sorted(Map.Entry.<String, Object>comparingByValue(
                        Comparator.comparingDouble(this::numberValue)
                ).reversed())
                .map(entry -> Map.<String, Object>of(
                        "name", entry.getKey(),
                        "value", numberValue(entry.getValue())
                ))
                .toList();

        long taskCount = count("""
                SELECT COUNT(*) FROM learning_task WHERE user_id = :userId
                """, userId);
        long completedTasks = count("""
                SELECT COUNT(*) FROM learning_task
                WHERE user_id = :userId AND status IN ('EVALUATED', 'COMPLETED')
                """, userId);
        long activeTasks = Math.max(taskCount - completedTasks, 0);
        long quizAttempts = count("""
                SELECT COUNT(*) FROM quiz_result WHERE user_id = :userId
                """, userId);
        int studyMinutes = intValue("""
                SELECT COALESCE(SUM(duration_minutes), 0) FROM learning_record WHERE user_id = :userId
                """, userId);

        double averageScore = doubleValue("""
                SELECT COALESCE(AVG(score), 0) FROM quiz_result WHERE user_id = :userId
                """, userId);
        double averageProgress = doubleValue("""
                SELECT COALESCE(AVG(progress), 0) FROM learning_record WHERE user_id = :userId
                """, userId);
        double masteryAverage = masteryRanking.stream()
                .mapToDouble(item -> numberValue(item.get("value")))
                .average()
                .orElse(0);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("task_count", taskCount);
        overview.put("completed_tasks", completedTasks);
        overview.put("active_tasks", activeTasks);
        overview.put("completion_rate", taskCount == 0 ? 0 : completedTasks * 1.0 / taskCount);
        overview.put("quiz_attempts", quizAttempts);
        overview.put("average_score", averageScore);
        overview.put("study_minutes", studyMinutes);
        overview.put("average_progress", averageProgress);
        overview.put("mastery_average", masteryAverage);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview", overview);
        result.put("score_trend", scoreTrend(userId));
        result.put("status_distribution", statusDistribution(userId));
        result.put("mastery_ranking", masteryRanking);
        result.put("weak_points", profile.getOrDefault("weak_points", List.of()));
        result.put("recommendations", recommendations(masteryRanking, quizAttempts, averageScore));
        return result;
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

    private long count(String sql, long userId) {
        return jdbc.sql(sql)
                .param("userId", userId)
                .query(Long.class)
                .single();
    }

    private int intValue(String sql, long userId) {
        Number value = jdbc.sql(sql)
                .param("userId", userId)
                .query(Number.class)
                .single();
        return value == null ? 0 : value.intValue();
    }

    private double doubleValue(String sql, long userId) {
        Number value = jdbc.sql(sql)
                .param("userId", userId)
                .query(Number.class)
                .single();
        return value == null ? 0 : value.doubleValue();
    }

    private List<Map<String, Object>> scoreTrend(long userId) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT score, created_at FROM quiz_result
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT 8
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("score", rs.getDouble("score"));
                    row.put("created_at", timeText(rs.getTimestamp("created_at")));
                    return row;
                })
                .list();
        List<Map<String, Object>> ordered = new ArrayList<>(rows);
        java.util.Collections.reverse(ordered);
        return ordered;
    }

    private List<Map<String, Object>> statusDistribution(long userId) {
        return jdbc.sql("""
                SELECT status, COUNT(*) AS total FROM learning_task
                WHERE user_id = :userId
                GROUP BY status
                ORDER BY total DESC
                """)
                .param("userId", userId)
                .query((rs, rowNum) -> Map.<String, Object>of(
                        "status", rs.getString("status"),
                        "total", rs.getLong("total")
                ))
                .list();
    }

    private List<String> recommendations(
            List<Map<String, Object>> masteryRanking,
            long quizAttempts,
            double averageScore
    ) {
        List<String> items = new ArrayList<>();
        if (quizAttempts == 0) {
            items.add("先完成一次知识检测，系统会生成更准确的掌握度趋势。");
        } else if (averageScore < 70) {
            items.add("最近测评均分偏低，建议先复习薄弱知识点再进入新任务。");
        } else {
            items.add("测评表现稳定，可以继续推进下一阶段学习任务。");
        }
        masteryRanking.stream()
                .min(Comparator.comparingDouble(item -> numberValue(item.get("value"))))
                .ifPresent(item -> items.add("优先巩固「" + item.get("name") + "」，当前掌握度约 "
                        + Math.round(numberValue(item.get("value")) * 100) + "%。"));
        return items;
    }

    private String timeText(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return 0;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException exception) {
            return 0;
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
