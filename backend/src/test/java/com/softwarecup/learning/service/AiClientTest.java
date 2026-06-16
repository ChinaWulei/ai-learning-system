package com.softwarecup.learning.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class AiClientTest {
    @Test
    void sendsTtsTextAsJsonBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiClient client = new AiClient(builder, new ObjectMapper(), "http://ai-service:8000");

        server.expect(requestTo("http://ai-service:8000/ai/v1/tts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", "audio/wav"))
                .andExpect(content().json("""
                        {"text":"讲解线程安全"}
                        """))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.parseMediaType("audio/wav")));

        assertThat(client.synthesizeSpeech("讲解线程安全")).containsExactly(1, 2, 3);
        server.verify();
    }

    @Test
    void explainsProtocolErrorFromAiService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiClient client = new AiClient(builder, new ObjectMapper(), "http://ai-service:8000");

        server.expect(requestTo("http://ai-service:8000/ai/v1/tts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Invalid HTTP request received."));

        assertThatThrownBy(() -> client.synthesizeSpeech("讲解线程安全"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI 服务协议错误")
                .hasMessageContaining("http://ai-service:8000");
    }
}
