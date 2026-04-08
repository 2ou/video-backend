package com.aivideo.canvas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity @Table(name = "workflow_run_nodes") @Data
public class WorkflowRunNode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long runId;
    private String nodeId;
    private String nodeType;
    private String status;
    @Column(columnDefinition = "JSON")
    private String inputJson;
    @Column(columnDefinition = "JSON")
    private String outputJson;
    private String provider;
    private String providerTaskId;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt = LocalDateTime.now();
}
