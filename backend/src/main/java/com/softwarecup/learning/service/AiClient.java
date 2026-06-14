package com.softwarecup.learning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
        return client.post().uri("/ai/v1/tutor/chat")
                .body(request).retrieve().body(Map.class);
    }
}

