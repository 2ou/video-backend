package com.aivideo.canvas.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

public class RunDtos {
    @Data @AllArgsConstructor
    public static class RunCreateData { private Long runId; private String status; }
}
