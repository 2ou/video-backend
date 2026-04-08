package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.dto.RunDtos;
import com.aivideo.canvas.entity.Project;
import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.service.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RunController {
    private final AuthService authService;
    private final ProjectService projectService;
    private final WorkflowService workflowService;
    private final RunService runService;

    public RunController(AuthService authService, ProjectService projectService, WorkflowService workflowService, RunService runService) {
        this.authService = authService;
        this.projectService = projectService;
        this.workflowService = workflowService;
        this.runService = runService;
    }

    @PostMapping("/api/v1/projects/{projectId}/run")
    public BaseResponse<RunDtos.RunCreateData> run(@RequestHeader("Authorization") String authorization, @PathVariable Long projectId){
        Long userId = authService.me(authorization).getId();
        Project project = projectService.get(userId, projectId);
        WorkflowRun run = workflowService.createAndRun(userId, project);
        return BaseResponse.ok(new RunDtos.RunCreateData(run.getId(), run.getStatus()));
    }

    @GetMapping("/api/v1/runs/{runId}")
    public BaseResponse<WorkflowRun> get(@RequestHeader("Authorization") String authorization, @PathVariable Long runId){
        return BaseResponse.ok(runService.getRun(authService.me(authorization).getId(), runId));
    }

    @GetMapping("/api/v1/runs/{runId}/nodes")
    public BaseResponse<List<WorkflowRunNode>> nodes(@RequestHeader("Authorization") String authorization, @PathVariable Long runId){
        runService.getRun(authService.me(authorization).getId(), runId);
        return BaseResponse.ok(runService.getNodes(runId));
    }
}
