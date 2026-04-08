package com.aivideo.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity @Table(name = "projects") @Data
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String coverUrl;
    private Long latestVersionId;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
