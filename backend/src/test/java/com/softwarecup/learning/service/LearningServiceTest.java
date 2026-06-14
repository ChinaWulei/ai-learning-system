package com.softwarecup.learning.service;

import com.softwarecup.learning.dto.LearningDtos.AnswerItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningServiceTest {
    @Test
    void submitUsesSnakeCaseQuizIdForAiService() {
        List<Map<String, String>> payload = LearningService.toAiAnswers(
                List.of(new AnswerItem("quiz-1", "A"))
        );

        assertEquals("quiz-1", payload.getFirst().get("quiz_id"));
        assertEquals("A", payload.getFirst().get("answer"));
    }
}
