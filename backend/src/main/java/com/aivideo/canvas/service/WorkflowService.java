package com.aivideo.canvas.service;

import com.aivideo.canvas.common.AppException;
import com.aivideo.canvas.entity.Project;
import com.aivideo.canvas.entity.ProjectVersion;
import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.repository.ProjectVersionRepository;
import com.aivideo.canvas.repository.WorkflowRunNodeRepository;
import com.aivideo.canvas.repository.WorkflowRunRepository;
import com.aivideo.canvas.worker.RunWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class WorkflowService {
    private static final Set<String> SUPPORTED = Set.of(
            // 原子化 Data Flow 节点
            "text", "image", "audio", "video_gen",
            // 旧节点类型（兼容）
            "input_video", "prompt_input", "kie_video_task", "output_video"
    );
    private final ProjectVersionRepository versionRepository;
    private final WorkflowRunRepository runRepository;
    private final WorkflowRunNodeRepository nodeRepository;
    private final RunWorker runWorker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WorkflowService(ProjectVersionRepository versionRepository, WorkflowRunRepository runRepository, WorkflowRunNodeRepository nodeRepository, RunWorker runWorker) {
        this.versionRepository = versionRepository;
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
        this.runWorker = runWorker;
    }

    public WorkflowRun createAndRun(Long userId, Project project){
        if (project.getLatestVersionId() == null) throw new AppException("VALIDATION_ERROR","请先保存画布");
        ProjectVersion version = versionRepository.findById(project.getLatestVersionId()).orElseThrow(() -> new AppException("NOT_FOUND","版本不存在"));
        WorkflowRun run = new WorkflowRun();
        run.setUserId(userId); run.setProjectId(project.getId()); run.setVersionId(version.getId()); run.setStatus("queued");
        run.setTriggerSource("manual"); run.setInputJson("{}"); run.setOutputJson("{}"); run.setStartedAt(LocalDateTime.now()); run.setErrorMessage("");
        run = runRepository.save(run);

        try {
            Map<String,Object> canvas = objectMapper.readValue(version.getCanvasJson(), new TypeReference<>(){});
            for (Map<String,Object> node : (List<Map<String,Object>>) canvas.getOrDefault("nodes", List.of())) {
                String type = String.valueOf(node.getOrDefault("type", ""));
                if (!SUPPORTED.contains(type)) continue;
                WorkflowRunNode rn = new WorkflowRunNode();
                rn.setRunId(run.getId()); rn.setNodeId(String.valueOf(node.getOrDefault("id", ""))); rn.setNodeType(type); rn.setStatus("queued");
                rn.setInputJson(objectMapper.writeValueAsString(node.getOrDefault("data", Map.of()))); rn.setOutputJson("{}"); rn.setProvider(""); rn.setProviderTaskId(""); rn.setErrorMessage("");
                nodeRepository.save(rn);
            }
        } catch (Exception e){ throw new AppException("VALIDATION_ERROR", e.getMessage()); }

        runWorker.execute(run.getId());
        return run;
    }
}
