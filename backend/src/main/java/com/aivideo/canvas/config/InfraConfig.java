package com.aivideo.canvas.config;

import com.aivideo.canvas.entity.User;
import com.aivideo.canvas.repository.UserRepository;
import com.aivideo.canvas.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class InfraConfig {
    @Bean
    public TaskScheduler taskScheduler(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("polling-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public CommandLineRunner init(UserRepository userRepository, AuthService authService, AppProperties appProperties){
        return args -> {
            for (String sub : new String[]{"raw","temp","result","cover"}) Files.createDirectories(Path.of(appProperties.getUploadDir(), sub));
            if (userRepository.findByUsername("admin").isEmpty()) {
                User user = new User();
                user.setUsername("admin"); user.setPasswordHash(authService.hash("123456")); user.setNickname("Administrator"); user.setRole("admin"); user.setStatus("active");
                userRepository.save(user);
            }
        };
    }
}
