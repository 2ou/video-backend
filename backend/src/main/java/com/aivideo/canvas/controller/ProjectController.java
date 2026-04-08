package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.dto.ProjectDtos;
import com.aivideo.canvas.entity.Project;
import com.aivideo.canvas.entity.ProjectVersion;
import com.aivideo.canvas.service.AuthService;
import com.aivideo.canvas.service.CanvasService;
import com.aivideo.canvas.service.ProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ProjectController {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AuthService authService;
    private final ProjectService projectService;
    private final CanvasService canvasService;

    public ProjectController(AuthService authService, ProjectService projectService, CanvasService canvasService) {
        this.authService = authService;
        this.projectService = projectService;
        this.canvasService = canvasService;
    }

    @PostMapping("/api/v1/projects")
    public BaseResponse<Project> create(@RequestHeader("Authorization") String authorization, @RequestBody ProjectDtos.CreateReq req){
        Long userId = authService.me(authorization).getId();
        return BaseResponse.ok(projectService.create(userId, req.getName(), req.getDescription()));
    }

    @GetMapping("/api/v1/projects")
    public BaseResponse<List<Project>> list(@RequestHeader("Authorization") String authorization){
        return BaseResponse.ok(projectService.list(authService.me(authorization).getId()));
    }

    @GetMapping("/api/v1/projects/{id}")
    public BaseResponse<Project> get(@RequestHeader("Authorization") String authorization, @PathVariable Long id){
        return BaseResponse.ok(projectService.get(authService.me(authorization).getId(), id));
    }

    @PutMapping("/api/v1/projects/{id}")
    public BaseResponse<Project> update(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @RequestBody ProjectDtos.UpdateReq req){
        Project p = projectService.get(authService.me(authorization).getId(), id);
        return BaseResponse.ok(projectService.update(p, req.getName(), req.getDescription(), req.getCoverUrl()));
    }

    @GetMapping("/api/v1/projects/{id}/canvas")
    public BaseResponse<Map<String,Object>> canvas(@RequestHeader("Authorization") String authorization, @PathVariable Long id){
        Long userId = authService.me(authorization).getId();
        Project p = projectService.get(userId, id);
        ProjectVersion v = canvasService.getOrInit(p, userId);
        try {
            Object canvasObj = objectMapper.readValue(v.getCanvasJson(), Object.class);
            return BaseResponse.ok(Map.of("project_id", id, "version_id", v.getId(), "version_no", v.getVersionNo(), "canvas_json", canvasObj));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @PutMapping("/api/v1/projects/{id}/canvas")
    public BaseResponse<Map<String,Object>> saveCanvas(@RequestHeader("Authorization") String authorization, @PathVariable Long id, @RequestBody ProjectDtos.CanvasReq req){
        Long userId = authService.me(authorization).getId();
        Project p = projectService.get(userId, id);
        ProjectVersion v = canvasService.save(p, userId, req.getCanvasJson(), req.getRemark());
        try {
            Object canvasObj = objectMapper.readValue(v.getCanvasJson(), Object.class);
            return BaseResponse.ok(Map.of("project_id", id, "version_id", v.getId(), "version_no", v.getVersionNo(), "canvas_json", canvasObj));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
