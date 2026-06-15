package com.softwarecup.learning.controller;

import com.softwarecup.learning.common.ApiResponse;
import com.softwarecup.learning.dto.LearningDtos.*;
import com.softwarecup.learning.service.LearningService;
import com.softwarecup.learning.service.ProfileService;
import com.softwarecup.learning.service.AiClient;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LearningController {
    private final LearningService learning;
    private final ProfileService profiles;
    private final AiClient ai;

    public LearningController(LearningService learning, ProfileService profiles, AiClient ai) {
        this.learning = learning;
        this.profiles = profiles;
        this.ai = ai;
    }

    @PostMapping("/learning/tasks")
    public ApiResponse<Map<String, Object>> create(
            Authentication authentication, @Valid @RequestBody CreateTaskRequest request
    ) {
        return ApiResponse.ok(learning.createTask(userId(authentication), request));
    }

    @GetMapping("/learning/tasks/{taskId}")
    public ApiResponse<Map<String, Object>> get(Authentication authentication, @PathVariable long taskId) {
        return ApiResponse.ok(learning.getTask(userId(authentication), taskId));
    }

    @PostMapping("/learning/tasks/{taskId}/progress")
    public ApiResponse<Void> progress(
            Authentication authentication,
            @PathVariable long taskId,
            @Valid @RequestBody ProgressRequest request
    ) {
        learning.recordProgress(userId(authentication), taskId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/learning/tasks/{taskId}/answers")
    public ApiResponse<Map<String, Object>> submit(
            Authentication authentication,
            @PathVariable long taskId,
            @Valid @RequestBody SubmitAnswersRequest request
    ) {
        return ApiResponse.ok(learning.submit(userId(authentication), taskId, request));
    }

    @PostMapping("/tutor/chat")
    public ApiResponse<Map<String, Object>> tutor(
            Authentication authentication, @Valid @RequestBody TutorRequest request
    ) {
        return ApiResponse.ok(learning.tutor(userId(authentication), request));
    }

    @PostMapping(value = "/tutor/speech", produces = "audio/wav")
    public ResponseEntity<byte[]> speech(
            Authentication authentication, @Valid @RequestBody SpeechRequest request
    ) {
        userId(authentication);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.noStore())
                .body(ai.synthesizeSpeech(request.text()));
    }

    @GetMapping("/users/me/profile")
    public ApiResponse<Map<String, Object>> profile(Authentication authentication) {
        return ApiResponse.ok(profiles.get(userId(authentication)));
    }

    private long userId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}

