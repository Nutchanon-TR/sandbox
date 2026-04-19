package com.sandbox.sandman.backend.services.MessageService;

import jakarta.annotation.PostConstruct;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GroqAiClient {

    private final ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;

    @PostConstruct
    void init() {
        this.chatClient = chatClientBuilder.build();
    }

    @CircuitBreaker(name = "groqAi", fallbackMethod = "fallback")
    @Retry(name = "groqAi")
    public String chat(Prompt prompt) {
        return chatClient.prompt(prompt).call().content();
    }

    public String fallback(Prompt prompt, Throwable throwable) {
        return "ขออภัย ระบบ AI ไม่สามารถตอบได้ชั่วคราว กรุณาลองใหม่อีกครั้ง";
    }
}
