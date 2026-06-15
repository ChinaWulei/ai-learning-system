package com.softwarecup.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Service
public class AiClient {
    private final RestClient client;

    public AiClient(RestClient.Builder builder, @Value("${ai.service-url}") String serviceUrl) {
        this.client = builder.baseUrl(serviceUrl).build();
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
            return client.post()
                    .uri("/ai/v1/tts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .body(byte[].class);
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

