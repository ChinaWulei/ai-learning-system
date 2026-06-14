package com.softwarecup.learning.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();

    public String create(long userId) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, userId);
        return token;
    }

    public Optional<Long> resolve(String token) {
        return Optional.ofNullable(sessions.get(token));
    }
}

