package com.aivideo.canvas.worker;

import com.aivideo.canvas.entity.ProjectVersion;
import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.repository.ProjectVersionRepository;
import com.aivideo.canvas.repository.WorkflowRunNodeRepository;
import com.aivideo.canvas.repository.WorkflowRunRepository;
import com.aivideo.canvas.service.KieService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class RunWorker {
    private final WorkflowRunRepository runRepository;
    private final WorkflowRunNodeRepository nodeRepository;
    private final ProjectVersionRepository versionRepository;
    private final KieService kieService;
    private final PollingWorker pollingWorker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RunWorker(WorkflowRunRepository runRepository, WorkflowRunNodeRepository nodeRepository, ProjectVersionRepository versionRepository, KieService kieService, PollingWorker pollingWorker) {
        this.runRepository = runRepository;
        this.nodeRepository = nodeRepository;
        this.versionRepository = versionRepository;
        this.kieService = kieService;
        this.pollingWorker = pollingWorker;
    }

    @Async
    public void execute(Long runId){
        WorkflowRun run = runRepository.findById(runId).orElse(null);
        if (run == null) return;
        ProjectVersion version = versionRepository.findById(run.getVersionId()).orElse(null);
        if (version == null) return;

        List<WorkflowRunNode> nodes = nodeRepository.findByRunIdOrderByIdAsc(runId);
        try {
            Map<String,Object> canvas = objectMapper.readValue(version.getCanvasJson(), new TypeReference<>(){});
            String prompt = "";
            Object assetId = null;

            // 安全地提取并遍历 nodes
            List<Map<String,Object>> canvasNodes = (List<Map<String,Object>>) canvas.get("nodes");
            if (canvasNodes != null) {
                for (Map<String,Object> n : canvasNodes) {
                    if ("prompt_input".equals(n.get("type"))) {
                        Map<String, Object> data = (Map<String, Object>) n.get("data");
                        if (data != null) prompt = String.valueOf(data.getOrDefault("text", ""));
                    }
                    if ("input_video".equals(n.get("type"))) {
                        Map<String, Object> data = (Map<String, Object>) n.get("data");
                        if (data != null) assetId = data.get("asset_id");
                    }
                }
            }

            for (WorkflowRunNode node : nodes) {
                if (!"kie_video_task".equals(node.getNodeType())) {
                    node.setStatus("success");
                    nodeRepository.save(node);
                    continue;
                }

                Map<String,Object> in = objectMapper.readValue(node.getInputJson(), new TypeReference<>(){});

                // 提取 params
                Map<String, Object> params = (Map<String, Object>) in.get("params");
                if (params == null) params = new HashMap<>();

                // 组装 payload
                Map<String,Object> payload = new HashMap<>();
                payload.put("model", params.getOrDefault("model", "video-model-a"));
                payload.put("params", params);
                payload.put("prompt", prompt);
                payload.put("input_asset_id", assetId);

                String taskId = kieService.submitVideoTask(payload);
                node.setProvider("kie");
                node.setProviderTaskId(taskId);
                node.setStatus("running");
                node.setStartedAt(LocalDateTime.now());
                nodeRepository.save(node);

                pollingWorker.schedule(node.getId());
            }
            run.setStatus("running");
            runRepository.save(run);
        } catch (Exception e){
            run.setStatus("failed");
            run.setErrorMessage(e.getMessage());
            runRepository.save(run);
        }
    }
}