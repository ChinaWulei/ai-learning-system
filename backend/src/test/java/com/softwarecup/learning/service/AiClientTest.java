package com.softwarecup.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiClientTest {
    @Test
    void sendsTtsTextAsJsonBody() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/v1/tts", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            byte[] response = new byte[]{1, 2, 3};
            exchange.getResponseHeaders().add("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AiClient client = new AiClient(
                    RestClient.builder(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThat(client.synthesizeSpeech("讲解线程安全", "ja-JP")).containsExactly(1, 2, 3);
            assertThat(contentType.get()).isEqualTo("application/json; charset=utf-8");
            assertThat(accept.get()).isEqualTo("audio/wav");
            var requestBody = new ObjectMapper().readTree(body.get());
            assertThat(requestBody.get("text").asText()).isEqualTo("讲解线程安全");
            assertThat(requestBody.get("language").asText()).isEqualTo("ja-JP");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explainsProtocolErrorFromAiService() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ai/v1/tts", exchange -> {
            byte[] response = "Invalid HTTP request received.".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            AiClient client = new AiClient(
                    RestClient.builder(),
                    new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort()
            );

            assertThatThrownBy(() -> client.synthesizeSpeech("讲解线程安全", "zh-CN"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AI 服务协议错误")
                    .hasMessageContaining("http://ai-service:8000");
        } finally {
            server.stop(0);
        }
    }
}
