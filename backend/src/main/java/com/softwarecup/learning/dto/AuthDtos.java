package com.softwarecup.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record FaceEnrollRequest(
            @NotBlank String username,
            @NotBlank String password,
            @NotEmpty List<Double> embedding
    ) {}
    public record FaceLoginRequest(@NotBlank String username, @NotEmpty List<Double> embedding) {}
    public record LoginResponse(String token, long userId, String nickname) {}
}

