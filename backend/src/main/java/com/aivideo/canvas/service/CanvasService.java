package com.aivideo.canvas.service;

import com.aivideo.canvas.entity.Project;
import com.aivideo.canvas.entity.ProjectVersion;
import com.aivideo.canvas.repository.ProjectRepository;
import com.aivideo.canvas.repository.ProjectVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class CanvasService {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProjectVersionRepository versionRepository;
    private final ProjectRepository projectRepository;
    private static final String DEFAULT_JSON = "{\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1},\"nodes\":[{\"id\":\"node_video_1\",\"type\":\"input_video\",\"position\":{\"x\":100,\"y\":100},\"data\":{\"label\":\"输入视频\",\"asset_id\":null}},{\"id\":\"node_prompt_1\",\"type\":\"prompt_input\",\"position\":{\"x\":100,\"y\":260},\"data\":{\"label\":\"提示词\",\"text\":\"make the video more cinematic\"}},{\"id\":\"node_kie_1\",\"type\":\"kie_video_task\",\"position\":{\"x\":420,\"y\":180},\"data\":{\"label\":\"KIE视频处理\",\"params\":{\"model\":\"video-model-a\",\"duration\":5,\"resolution\":\"1080p\"}}},{\"id\":\"node_output_1\",\"type\":\"output_video\",\"position\":{\"x\":760,\"y\":180},\"data\":{\"label\":\"输出视频\"}}],\"edges\":[{\"id\":\"e1\",\"source\":\"node_video_1\",\"target\":\"node_kie_1\"},{\"id\":\"e2\",\"source\":\"node_prompt_1\",\"target\":\"node_kie_1\"},{\"id\":\"e3\",\"source\":\"node_kie_1\",\"target\":\"node_output_1\"}]}";

    public CanvasService(ProjectVersionRepository versionRepository, ProjectRepository projectRepository) {
        this.versionRepository = versionRepository;
        this.projectRepository = projectRepository;
    }

    public ProjectVersion getOrInit(Project project, Long userId){
        if (project.getLatestVersionId() != null) {
            return versionRepository.findById(project.getLatestVersionId()).orElseGet(() -> create(project,userId,DEFAULT_JSON,"init"));
        }
        return create(project,userId,DEFAULT_JSON,"init");
    }

    public ProjectVersion save(Project project, Long userId, Object canvasJson, String remark){
        try {
            String json = canvasJson instanceof String ? (String) canvasJson : objectMapper.writeValueAsString(canvasJson);
            return create(project,userId, json,remark);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProjectVersion create(Project project, Long userId, String canvasJson, String remark){
        Integer latest = versionRepository.findTopByProjectIdOrderByVersionNoDesc(project.getId()).map(ProjectVersion::getVersionNo).orElse(0);
        ProjectVersion v = new ProjectVersion();
        v.setProjectId(project.getId()); v.setVersionNo(latest+1); v.setCanvasJson(canvasJson); v.setRemark(remark); v.setCreatedBy(userId);
        v = versionRepository.save(v);
        project.setLatestVersionId(v.getId());
        projectRepository.save(project);
        return v;
    }
}
