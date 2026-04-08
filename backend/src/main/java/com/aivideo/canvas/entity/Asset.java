package com.aivideo.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity @Table(name = "assets") @Data
public class Asset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long projectId;
    private String fileName;
    private String fileType;
    private String mimeType;
    private Long fileSize;
    private String storageType;
    private String storagePath;
    private String fileUrl;
    private Integer width;
    private Integer height;
    private Double duration;
    @Column(columnDefinition = "JSON")
    private String metaJson;
    private LocalDateTime createdAt = LocalDateTime.now();
}
