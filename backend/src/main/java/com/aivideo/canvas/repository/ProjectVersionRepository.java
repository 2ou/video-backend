package com.aivideo.canvas.repository;

import com.aivideo.canvas.entity.ProjectVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, Long> {
    Optional<ProjectVersion> findTopByProjectIdOrderByVersionNoDesc(Long projectId);
}
