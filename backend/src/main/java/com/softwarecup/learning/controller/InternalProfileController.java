package com.softwarecup.learning.controller;

import com.softwarecup.learning.common.ApiResponse;
import com.softwarecup.learning.service.ProfileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/internal/users")
public class InternalProfileController {
    private final ProfileService profiles;
    private final String internalToken;

    public InternalProfileController(
            ProfileService profiles, @Value("${ai.internal-token}") String internalToken
    ) {
        this.profiles = profiles;
        this.internalToken = internalToken;
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<Map<String, Object>> get(
            @RequestHeader("X-Internal-Token") String token, @PathVariable long userId
    ) {
        verify(token);
        return ApiResponse.ok(profiles.get(userId));
    }

    @PutMapping("/{userId}/profile")
    public ApiResponse<Void> save(
            @RequestHeader("X-Internal-Token") String token,
            @PathVariable long userId,
            @RequestBody Map<String, Object> profile
    ) {
        verify(token);
        profiles.save(userId, profile);
        return ApiResponse.ok(null);
    }

    private void verify(String token) {
        if (!internalToken.equals(token)) {
            throw new IllegalArgumentException("内部服务令牌无效");
        }
    }
}

