package com.aivideo.canvas.repository;

import com.aivideo.canvas.entity.WorkflowRunNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRunNodeRepository extends JpaRepository<WorkflowRunNode, Long> {
    List<WorkflowRunNode> findByRunIdOrderByIdAsc(Long runId);
}
