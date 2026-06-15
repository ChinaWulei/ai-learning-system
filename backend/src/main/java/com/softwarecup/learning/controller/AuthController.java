package com.softwarecup.learning.controller;

import com.softwarecup.learning.common.ApiResponse;
import com.softwarecup.learning.dto.AuthDtos.FaceEnrollRequest;
import com.softwarecup.learning.dto.AuthDtos.FaceLoginRequest;
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

    @PostMapping("/face/enroll")
    public ApiResponse<Void> enrollFace(@Valid @RequestBody FaceEnrollRequest request) {
        auth.enrollFace(request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/face/login")
    public ApiResponse<LoginResponse> faceLogin(@Valid @RequestBody FaceLoginRequest request) {
        return ApiResponse.ok(auth.faceLogin(request));
    }
}

