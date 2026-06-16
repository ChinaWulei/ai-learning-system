package com.softwarecup.learning.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class AiClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String serviceUrl;

    public AiClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${ai.service-url}") String serviceUrl
    ) {
        this.serviceUrl = serviceUrl.replaceAll("/+$", "");
        this.client = builder.baseUrl(this.serviceUrl).build();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serviceUrl + "/ai/v1/tts"))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(150))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "audio/wav")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            handleSpeechError(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
            throw new IllegalStateException("数字教师语音生成失败");
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数字教师语音请求序列化失败", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("数字教师语音服务连接失败：" + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("数字教师语音请求被中断", exception);
        } catch (RestClientResponseException exception) {
            handleSpeechError(exception.getStatusCode().value(), exception.getResponseBodyAsString());
            throw new IllegalStateException("数字教师语音生成失败", exception);
        }
    }

    private void handleSpeechError(int statusCode, String responseBody) {
        if (statusCode == 400 && responseBody.contains("Invalid HTTP request received")) {
            throw new IllegalStateException(
                    "AI 服务协议错误：请确认后端环境变量 AI_SERVICE_URL 使用 http://ai-service:8000，"
                            + "不要使用 https、公网域名或浏览器访问地址"
            );
        }
        throw new IllegalStateException(
                "数字教师语音生成失败（HTTP %d）：%s".formatted(statusCode, responseBody)
        );
    }
}

