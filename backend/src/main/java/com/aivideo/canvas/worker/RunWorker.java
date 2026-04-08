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
            for (Map<String,Object> n : (List<Map<String,Object>>) canvas.getOrDefault("nodes", List.of())) {
                if ("prompt_input".equals(n.get("type"))) prompt = String.valueOf(((Map<?,?>)n.get("data")).getOrDefault("text", ""));
                if ("input_video".equals(n.get("type"))) assetId = ((Map<?,?>)n.get("data")).get("asset_id");
            }
            for (WorkflowRunNode node : nodes) {
                if (!"kie_video_task".equals(node.getNodeType())) { node.setStatus("success"); nodeRepository.save(node); continue; }
                Map<String,Object> in = objectMapper.readValue(node.getInputJson(), new TypeReference<>(){});
                Map<String,Object> payload = new HashMap<>();
                payload.put("model", ((Map<?,?>)in.getOrDefault("params", Map.of())).getOrDefault("model", "video-model-a"));
                payload.put("params", in.getOrDefault("params", Map.of()));
                payload.put("prompt", prompt);
                payload.put("input_asset_id", assetId);
                Map<String,Object> payload = Map.of("model", ((Map<?,?>)in.getOrDefault("params", Map.of())).getOrDefault("model", "video-model-a"), "params", in.getOrDefault("params", Map.of()), "prompt", prompt, "input_asset_id", assetId);
                String taskId = kieService.submitVideoTask(payload);
                node.setProvider("kie"); node.setProviderTaskId(taskId); node.setStatus("running"); node.setStartedAt(LocalDateTime.now());
                nodeRepository.save(node);
                pollingWorker.schedule(node.getId());
            }
            run.setStatus("running");
            runRepository.save(run);
        } catch (Exception e){
            run.setStatus("failed"); run.setErrorMessage(e.getMessage()); runRepository.save(run);
        }
    }
}
