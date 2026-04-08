package com.aivideo.canvas.worker;

import com.aivideo.canvas.entity.WorkflowRun;
import com.aivideo.canvas.entity.WorkflowRunNode;
import com.aivideo.canvas.repository.WorkflowRunNodeRepository;
import com.aivideo.canvas.repository.WorkflowRunRepository;
import com.aivideo.canvas.service.KieService;
import com.aivideo.canvas.service.StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class PollingWorker {
    private final TaskScheduler taskScheduler;
    private final WorkflowRunNodeRepository nodeRepository;
    private final WorkflowRunRepository runRepository;
    private final KieService kieService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PollingWorker(TaskScheduler taskScheduler, WorkflowRunNodeRepository nodeRepository, WorkflowRunRepository runRepository, KieService kieService, StorageService storageService) {
        this.taskScheduler = taskScheduler;
        this.nodeRepository = nodeRepository;
        this.runRepository = runRepository;
        this.kieService = kieService;
        this.storageService = storageService;
    }

    public void schedule(Long runNodeId){
        scheduleWithAttempt(runNodeId, 1, 3);
    }

    private void scheduleWithAttempt(Long runNodeId, int attempt, int delay){
        taskScheduler.schedule(() -> poll(runNodeId, attempt), Instant.now().plusSeconds(delay));
    }

    private void poll(Long runNodeId, int attempt){
        WorkflowRunNode node = nodeRepository.findById(runNodeId).orElse(null);
        if (node == null || node.getProviderTaskId() == null || node.getProviderTaskId().isBlank()) return;
        WorkflowRun run = runRepository.findById(node.getRunId()).orElse(null);
        if (run == null) return;

        if (attempt > 60){
            node.setStatus("timeout"); node.setErrorMessage("Polling timeout"); node.setFinishedAt(LocalDateTime.now());
            run.setStatus("timeout"); run.setFinishedAt(LocalDateTime.now());
            nodeRepository.save(node); runRepository.save(run); return;
        }

        Map<String,Object> raw = kieService.queryTask(node.getProviderTaskId());
        String status = kieService.normalizeStatus(String.valueOf(raw.getOrDefault("status", ((Map<?,?>)raw.getOrDefault("data", Map.of())).get("status"))));
        if (List.of("queued","running").contains(status)) { scheduleWithAttempt(runNodeId, attempt+1, 5); return; }

        if ("success".equals(status)) {
            try {
                List<String> urls = kieService.extractResultUrls(raw);
                List<String> localUrls = urls.stream().map(u -> {
                    try { return String.valueOf(storageService.downloadRemoteFile(u, "result").get("fileUrl")); }
                    catch (Exception e) { throw new RuntimeException(e); }
                }).toList();
                node.setOutputJson(objectMapper.writeValueAsString(Map.of("result_urls", localUrls)));
                node.setStatus("success"); node.setFinishedAt(LocalDateTime.now());
                nodeRepository.save(node);
                refreshRun(run);
                return;
            } catch (Exception e){
                node.setStatus("failed"); node.setErrorMessage(e.getMessage()); nodeRepository.save(node);
            }
        }

        node.setStatus("failed");
        node.setErrorMessage(String.valueOf(raw.getOrDefault("message", "KIE task failed")));
        node.setFinishedAt(LocalDateTime.now());
        run.setStatus("failed"); run.setErrorMessage(node.getErrorMessage()); run.setFinishedAt(LocalDateTime.now());
        nodeRepository.save(node); runRepository.save(run);
    }

    private void refreshRun(WorkflowRun run){
        List<WorkflowRunNode> nodes = nodeRepository.findByRunIdOrderByIdAsc(run.getId());
        boolean allSuccess = nodes.stream().allMatch(n -> List.of("success","skipped").contains(n.getStatus()));
        if (allSuccess){ run.setStatus("success"); run.setFinishedAt(LocalDateTime.now()); }
        else run.setStatus("running");
        runRepository.save(run);
    }
}
