package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.repository.WorkflowRunNodeRepository;
import com.aivideo.canvas.repository.WorkflowRunRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RunService {
    private final WorkflowRunRepository runRepository;
    private final WorkflowRunNodeRepository nodeRepository;

    public RunService(WorkflowRunRepository runRepository, WorkflowRunNodeRepository nodeRepository) {
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
    }

    public WorkflowRun getRun(Long userId, Long runId){
        WorkflowRun r = runRepository.findById(runId).orElseThrow(() -> new AppException("NOT_FOUND","运行记录不存在"));
        if (!r.getUserId().equals(userId)) throw new AppException("NOT_FOUND","运行记录不存在");
        return r;
    }

    public List<WorkflowRunNode> getNodes(Long runId){ return nodeRepository.findByRunIdOrderByIdAsc(runId); }
}
