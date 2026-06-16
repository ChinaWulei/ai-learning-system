package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class AiClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public AiClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${ai.service-url}") String serviceUrl
    ) {
        this.client = builder.baseUrl(serviceUrl).build();
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generatePlan(Map<String, Object> request) {
        return client.post().uri("/ai/v1/plans/generate")
                .body(request).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluate(Map<String, Object> request) {
        return client.post().uri("/ai/v1/evaluations/answer")
                .body(request).retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> tutor(Map<String, Object> request) {
        try {
            return client.post().uri("/ai/v1/tutor/chat")
                    .body(request).retrieve().body(Map.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "AI 助教请求失败（HTTP %d）：%s".formatted(
                            exception.getStatusCode().value(),
                            exception.getResponseBodyAsString()
                    ),
                    exception
            );
        }
    }

    public byte[] synthesizeSpeech(String text) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("text", text));
            return client.post()
                    .uri("/ai/v1/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.parseMediaType("audio/wav"))
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数字教师语音请求序列化失败", exception);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "数字教师语音生成失败（HTTP %d）：%s".formatted(
                            exception.getStatusCode().value(),
                            exception.getResponseBodyAsString()
                    ),
                    exception
            );
        }
    }
}

