package com.softwarecup.learning.service;

import jakarta.annotation.PreDestroy;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class KnowledgeGraphService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);

    private final boolean enabled;
    private final Driver driver;

    public KnowledgeGraphService(
            @Value("${knowledge-graph.enabled:false}") boolean enabled,
            @Value("${knowledge-graph.uri:bolt://localhost:7687}") String uri,
            @Value("${knowledge-graph.username:neo4j}") String username,
            @Value("${knowledge-graph.password:learning123}") String password
    ) {
        this.enabled = enabled;
        this.driver = enabled ? GraphDatabase.driver(uri, AuthTokens.basic(username, password)) : null;
    }

    public void syncTask(
            long userId,
            long taskId,
            String topic,
            List<Map<String, Object>> stages,
            List<Map<String, Object>> quizzes
    ) {
        if (!enabled) return;
        try (Session session = driver.session()) {
            session.executeWrite(tx -> {
                tx.run("""
                        MERGE (u:User {id: $userId})
                        MERGE (t:LearningTask {id: $taskId})
                        SET t.topic = $topic, t.updatedAt = datetime()
                        MERGE (u)-[:LEARNING]->(t)
                        """, Values.parameters("userId", userId, "taskId", taskId, "topic", topic));
                for (int i = 0; i < stages.size(); i++) {
                    Map<String, Object> stage = stages.get(i);
                    String name = text(stage.getOrDefault("name", "阶段 " + (i + 1)));
                    tx.run("""
                            MERGE (k:KnowledgePoint {name: $name})
                            SET k.objective = $objective
                            WITH k
                            MATCH (t:LearningTask {id: $taskId})
                            MERGE (t)-[r:INCLUDES]->(k)
                            SET r.order = $order, r.hours = $hours
                            """, Values.parameters(
                            "taskId", taskId,
                            "name", name,
                            "objective", text(stage.get("objective")),
                            "order", number(stage.getOrDefault("order", i + 1)).intValue(),
                            "hours", number(stage.getOrDefault("hours", 0)).doubleValue()
                    ));
                }
                for (Map<String, Object> quiz : quizzes) {
                    String quizId = text(quiz.get("id"));
                    if (quizId.isBlank()) continue;
                    String point = text(quiz.get("knowledge_point"));
                    if (point.isBlank()) point = text(quiz.getOrDefault("question", topic));
                    tx.run("""
                            MERGE (k:KnowledgePoint {name: $point})
                            MERGE (q:Quiz {id: $quizId})
                            SET q.question = $question, q.type = $type, q.difficulty = $difficulty
                            MERGE (k)-[:CHECKED_BY]->(q)
                            """, Values.parameters(
                            "point", point,
                            "quizId", quizId,
                            "question", text(quiz.get("question")),
                            "type", text(quiz.get("type")),
                            "difficulty", text(quiz.get("difficulty"))
                    ));
                }
                return null;
            });
        } catch (RuntimeException exception) {
            log.warn("Knowledge graph sync failed: {}", exception.getMessage());
        }
    }

    public Map<String, Object> graph(long userId) {
        if (!enabled) {
            return Map.of("enabled", false, "nodes", List.of(), "edges", List.of());
        }
        try (Session session = driver.session()) {
            return session.executeRead(tx -> {
                Result result = tx.run("""
                        MATCH (u:User {id: $userId})-[:LEARNING]->(t:LearningTask)
                        OPTIONAL MATCH (t)-[r:INCLUDES]->(k:KnowledgePoint)
                        OPTIONAL MATCH (k)-[:CHECKED_BY]->(q:Quiz)
                        RETURN t, r, k, collect(q) AS quizzes
                        ORDER BY t.id, r.order
                        """, Values.parameters("userId", userId));
                Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
                List<Map<String, Object>> edges = new ArrayList<>();
                while (result.hasNext()) {
                    Record row = result.next();
                    var task = row.get("t").asNode();
                    String taskId = "task-" + task.get("id").asLong();
                    nodes.putIfAbsent(taskId, node(taskId, task.get("topic").asString("学习任务"), "task"));
                    if (!row.get("k").isNull()) {
                        var knowledge = row.get("k").asNode();
                        String knowledgeId = "knowledge-" + knowledge.id();
                        nodes.putIfAbsent(knowledgeId, node(knowledgeId, knowledge.get("name").asString(), "knowledge"));
                        edges.add(edge(taskId, knowledgeId, "包含"));
                        row.get("quizzes").asList(value -> value.asNode()).forEach(quiz -> {
                            String quizId = "quiz-" + quiz.get("id").asString();
                            nodes.putIfAbsent(quizId, node(quizId, quiz.get("question").asString("练习题"), "quiz"));
                            edges.add(edge(knowledgeId, quizId, "检测"));
                        });
                    }
                }
                return Map.of("enabled", true, "nodes", List.copyOf(nodes.values()), "edges", edges);
            });
        } catch (RuntimeException exception) {
            log.warn("Knowledge graph query failed: {}", exception.getMessage());
            return Map.of("enabled", true, "error", exception.getMessage(), "nodes", List.of(), "edges", List.of());
        }
    }

    @PreDestroy
    public void close() {
        if (driver != null) driver.close();
    }

    private Map<String, Object> node(String id, String label, String type) {
        return Map.of("id", id, "label", label, "type", type);
    }

    private Map<String, Object> edge(String source, String target, String label) {
        return Map.of("source", source, "target", target, "label", label);
    }

    private static String text(Object value) {
        return Objects.toString(value, "").trim();
    }

    private static Number number(Object value) {
        if (value instanceof Number number) return number;
        try {
            return Double.parseDouble(Objects.toString(value, "0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
