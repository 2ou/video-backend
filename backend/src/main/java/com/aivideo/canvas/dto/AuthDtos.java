package com.aivideo.canvas.dto;

import lombok.Data;

public class AuthDtos {
    @Data
    public static class LoginRequest { private String username; private String password; }
    @Data
    public static class LoginData { private String accessToken; private String tokenType = "bearer"; }
}
