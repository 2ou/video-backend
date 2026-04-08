package com.aivideo.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity @Table(name = "project_versions") @Data
public class ProjectVersion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long projectId;
    private Integer versionNo;
    @Column(columnDefinition = "JSON")
    private String canvasJson;
    @Column(columnDefinition = "TEXT")
    private String remark;
    private Long createdBy;
    private LocalDateTime createdAt = LocalDateTime.now();
}
