package com.aivideo.canvas.dto;

import lombok.Data;

public class ProjectDtos {
    @Data
    public static class CreateReq { private String name; private String description; }
    @Data
    public static class UpdateReq { private String name; private String description; private String coverUrl; }
    @Data
    public static class CanvasReq { private Object canvasJson; private String remark = "save"; }
}
