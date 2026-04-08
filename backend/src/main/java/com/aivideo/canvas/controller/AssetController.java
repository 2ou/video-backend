package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.entity.Asset;
import com.aivideo.canvas.service.AssetService;
import com.aivideo.canvas.service.AuthService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {
    private final AssetService assetService;
    private final AuthService authService;

    public AssetController(AssetService assetService, AuthService authService) { this.assetService = assetService; this.authService = authService; }

    @PostMapping("/upload")
    public BaseResponse<Asset> upload(@RequestHeader("Authorization") String authorization,
                                      @RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "project_id", required = false) Long projectId) throws Exception {
        Long userId = authService.me(authorization).getId();
        return BaseResponse.ok(assetService.upload(userId, projectId, file));
    }
}
