package com.aivideo.canvas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CanvasBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CanvasBackendApplication.class, args);
    }
}
