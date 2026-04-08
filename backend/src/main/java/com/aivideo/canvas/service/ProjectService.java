package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.entity.Project;
import com.aivideo.canvas.repository.ProjectRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) { this.projectRepository = projectRepository; }

    public Project create(Long userId, String name, String description){
        Project p = new Project();
        p.setUserId(userId); p.setName(name); p.setDescription(description); p.setCoverUrl("");
        return projectRepository.save(p);
    }

    public List<Project> list(Long userId){ return projectRepository.findByUserIdOrderByIdDesc(userId); }

    public Project get(Long userId, Long projectId){
        Project p = projectRepository.findById(projectId).orElseThrow(() -> new AppException("NOT_FOUND","项目不存在"));
        if (!p.getUserId().equals(userId)) throw new AppException("FORBIDDEN","无权限");
        return p;
    }

    public Project update(Project p, String name, String description, String coverUrl){
        p.setName(name); p.setDescription(description); p.setCoverUrl(coverUrl); p.setUpdatedAt(LocalDateTime.now());
        return projectRepository.save(p);
    }
}
