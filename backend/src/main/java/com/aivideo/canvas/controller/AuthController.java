package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.dto.AuthDtos;
import com.aivideo.canvas.entity.User;
import com.aivideo.canvas.service.AuthService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public BaseResponse<AuthDtos.LoginData> login(@RequestBody AuthDtos.LoginRequest req){
        String token = authService.login(req.getUsername(), req.getPassword());
        AuthDtos.LoginData d = new AuthDtos.LoginData(); d.setAccessToken(token);
        return BaseResponse.ok(d);
    }

    @GetMapping("/me")
    public BaseResponse<Map<String,Object>> me(@RequestHeader("Authorization") String authorization){
        User user = authService.me(authorization);
        return BaseResponse.ok(Map.of("id",user.getId(),"username",user.getUsername(),"nickname",user.getNickname(),"role",user.getRole(),"status",user.getStatus()));
    }
}
