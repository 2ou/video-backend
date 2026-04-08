package com.aivideo.canvas.repository;

import com.aivideo.canvas.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByUserIdOrderByIdDesc(Long userId);
}
