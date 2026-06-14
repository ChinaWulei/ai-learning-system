package com.softwarecup.learning.controller;

import com.softwarecup.learning.common.ApiResponse;
import com.softwarecup.learning.dto.AuthDtos.LoginRequest;
import com.softwarecup.learning.dto.AuthDtos.LoginResponse;
import com.softwarecup.learning.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(auth.login(request));
    }
}

