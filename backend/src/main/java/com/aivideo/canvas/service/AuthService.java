package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.entity.User;
import com.aivideo.canvas.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public String login(String username, String password){
        User user = userRepository.findByUsername(username).orElseThrow(() -> new AppException("UNAUTHORIZED","用户名或密码错误"));
        if (!encoder.matches(password, user.getPasswordHash())) throw new AppException("UNAUTHORIZED","用户名或密码错误");
        return jwtService.createToken(user.getId());
    }

    public User me(String bearer){
        Long userId = jwtService.parseUserId(bearer.replace("Bearer ", ""));
        return userRepository.findById(userId).orElseThrow(() -> new AppException("UNAUTHORIZED","用户不存在"));
    }

    public String hash(String password){ return encoder.encode(password); }
}
