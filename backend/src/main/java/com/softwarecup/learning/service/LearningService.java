package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softwarecup.learning.dto.LearningDtos.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class LearningService {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final AiClient ai;
    private final ProfileService profiles;

    public LearningService(JdbcClient jdbc, ObjectMapper json, AiClient ai, ProfileService profiles) {
        this.jdbc = jdbc;
        this.json = json;
        this.ai = ai;
        this.profiles = profiles;
    }

    @Transactional
    public Map<String, Object> createTask(long userId, CreateTaskRequest request) {
        var keyHolder = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO learning_task(user_id, topic, goal) VALUES (:userId, :topic, :goal)
                """)
                .param("userId", userId)
                .param("topic", request.topic())
                .param("goal", request.goal())
                .update(keyHolder, "id");
        long taskId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> aiResponse = ai.generatePlan(new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "user_id", userId,
                "task_id", taskId,
                "topic", request.topic(),
                "goal", Objects.requireNonNullElse(request.goal(), ""),
                "user_profile", profiles.get(userId)
        )));
        Map<String, Object> data = map(aiResponse.get("data"));
        jdbc.sql("""
                UPDATE learning_task SET status = 'LEARNING', current_stage = '概念建立',
                    plan_json = :plan, resources_json = :resources, content_json = :content,
                    updated_at = CURRENT_TIMESTAMP WHERE id = :taskId
                """)
                .param("plan", write(data.get("learning_plan")))
                .param("resources", write(data.get("resources")))
                .param("content", write(data.get("content")))
                .param("taskId", taskId)
                .update();
        saveQuizzes(taskId, list(data.get("quizzes")));
        log(requestId, taskId, "GENERATE_PLAN", aiResponse, true);
        return getTask(userId, taskId);
    }

    public Map<String, Object> getTask(long userId, long taskId) {
        Map<String, Object> task = taskRow(userId, taskId);
        LinkedHashMap<String, Object> result = new LinkedHashMap<>(task);
        result.remove("plan_json");
        result.remove("resources_json");
        result.remove("content_json");
        result.put("learning_plan", read(task.get("plan_json")));
        result.put("resources", read(task.get("resources_json")));
        result.put("content", read(task.get("content_json")));
        result.put("quizzes", getQuizzes(taskId, false));
        return result;
    }

    public void recordProgress(long userId, long taskId, ProgressRequest request) {
        assertOwner(userId, taskId);
        jdbc.sql("""
                INSERT INTO learning_record(user_id, task_id, knowledge_point, action_type,
                    duration_minutes, progress, detail_json)
                VALUES (:userId, :taskId, :point, :action, :duration, :progress, :detail)
                """)
                .param("userId", userId)
                .param("taskId", taskId)
                .param("point", request.knowledgePoint())
                .param("action", request.actionType())
                .param("duration", request.durationMinutes())
                .param("progress", request.progress())
                .param("detail", write(request.detail()))
                .update();
    }

    @Transactional
    public Map<String, Object> submit(long userId, long taskId, SubmitAnswersRequest request) {
        Map<String, Object> task = taskRow(userId, taskId);
        List<Map<String, Object>> quizzes = getQuizzes(taskId, true);
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> aiResponse = ai.evaluate(Map.of(
                "request_id", requestId,
                "user_id", userId,
                "task_id", taskId,
                "topic", task.get("topic"),
                "quizzes", quizzes,
                "answers", toAiAnswers(request.answers()),
                "user_profile", profiles.get(userId)
        ));
        Map<String, Object> data = map(aiResponse.get("data"));
        Map<String, Object> evaluation = map(data.get("evaluation"));
        profiles.save(userId, map(data.get("profile_update")));
        jdbc.sql("""
                INSERT INTO quiz_result(task_id, user_id, answers_json, score, feedback_json)
                VALUES (:taskId, :userId, :answers, :score, :feedback)
                """)
                .param("taskId", taskId)
                .param("userId", userId)
                .param("answers", write(request.answers()))
                .param("score", evaluation.get("score"))
                .param("feedback", write(evaluation))
                .update();
        double score = ((Number) evaluation.get("score")).doubleValue();
        String status = score >= 85 ? "EVALUATED" : "REMEDIATION";
        jdbc.sql("UPDATE learning_task SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("status", status)
                .param("id", taskId)
                .update();
        log(requestId, taskId, "EVALUATE", aiResponse, true);
        return data;
    }

    static List<Map<String, String>> toAiAnswers(List<AnswerItem> answers) {
        return answers.stream()
                .map(answer -> Map.of(
                        "quiz_id", answer.quizId(),
                        "answer", answer.answer()
                ))
                .toList();
    }

    public Map<String, Object> tutor(long userId, TutorRequest request) {
        String requestId = UUID.randomUUID().toString();
        return map(ai.tutor(new LinkedHashMap<>(Map.of(
                "request_id", requestId,
                "user_id", userId,
                "task_id", Objects.requireNonNullElse(request.taskId(), 0L),
                "message", request.message(),
                "user_profile", profiles.get(userId)
        ))).get("data"));
    }

    private List<Map<String, Object>> getQuizzes(long taskId, boolean includeAnswer) {
        return jdbc.sql("SELECT * FROM quiz WHERE task_id = :taskId ORDER BY created_at")
                .param("taskId", taskId)
                .query((rs, row) -> {
                    Map<String, Object> quiz = map(read(rs.getString("content_json")));
                    if (!includeAnswer) {
                        quiz.remove("answer");
                        quiz.remove("explanation");
                    }
                    return quiz;
                })
                .list();
    }

    private void saveQuizzes(long taskId, List<Map<String, Object>> quizzes) {
        for (Map<String, Object> quiz : quizzes) {
            jdbc.sql("""
                    INSERT INTO quiz(id, task_id, knowledge_point, question_type, difficulty,
                        content_json, reference_answer)
                    VALUES (:id, :taskId, :point, :type, :difficulty, :content, :answer)
                    """)
                    .param("id", quiz.get("id"))
                    .param("taskId", taskId)
                    .param("point", quiz.get("knowledge_point"))
                    .param("type", quiz.get("type"))
                    .param("difficulty", quiz.get("difficulty"))
                    .param("content", write(quiz))
                    .param("answer", write(quiz.get("answer")))
                    .update();
        }
    }

    private Map<String, Object> taskRow(long userId, long taskId) {
        int count = jdbc.sql("""
                SELECT COUNT(*) FROM learning_task WHERE id = :id AND user_id = :userId
                """)
                .param("id", taskId)
                .param("userId", userId)
                .query(Integer.class)
                .single();
        if (count == 0) {
            throw new IllegalArgumentException("学习任务不存在");
        }
        return jdbc.sql("SELECT * FROM learning_task WHERE id = :id AND user_id = :userId")
                .param("id", taskId)
                .param("userId", userId)
                .query()
                .singleRow();
    }

    private void assertOwner(long userId, long taskId) {
        taskRow(userId, taskId);
    }

    private void log(String requestId, long taskId, String operation, Object trace, boolean success) {
        jdbc.sql("""
                INSERT INTO ai_execution_log(request_id, task_id, operation, trace_json, success)
                VALUES (:requestId, :taskId, :operation, :trace, :success)
                """)
                .param("requestId", requestId)
                .param("taskId", taskId)
                .param("operation", operation)
                .param("trace", write(trace))
                .param("success", success)
                .update();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化JSON", exception);
        }
    }

    private Object read(Object value) {
        if (value == null) return null;
        try {
            return json.readValue(value.toString(), Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSON数据损坏", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value == null) return new LinkedHashMap<>();
        return json.convertValue(value, new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> list(Object value) {
        if (value == null) return List.of();
        return json.convertValue(value, new TypeReference<>() {});
    }
}
