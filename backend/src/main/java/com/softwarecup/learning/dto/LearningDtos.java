package com.softwarecup.learning.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public final class LearningDtos {
    private LearningDtos() {}

    public record CreateTaskRequest(@NotBlank String topic, String goal) {}
    public record ProgressRequest(
            String knowledgePoint,
            @NotBlank String actionType,
            @Min(0) int durationMinutes,
            @Min(0) @Max(100) double progress,
            Map<String, Object> detail
    ) {}
    public record AnswerItem(@NotBlank String quizId, @NotBlank String answer) {}
    public record SubmitAnswersRequest(@NotEmpty List<AnswerItem> answers) {}
    public record TutorRequest(@NotBlank String message, Long taskId) {}
    public record SpeechRequest(@NotBlank @Size(max = 6000) String text) {}
}

