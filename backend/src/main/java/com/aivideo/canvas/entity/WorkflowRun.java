package com.aivideo.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity @Table(name = "workflow_runs") @Data
public class WorkflowRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long projectId;
    private Long versionId;
    private String status;
    private String triggerSource;
    @Column(columnDefinition = "JSON")
    private String inputJson;
    @Column(columnDefinition = "JSON")
    private String outputJson;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    private LocalDateTime createdAt = LocalDateTime.now();
}
