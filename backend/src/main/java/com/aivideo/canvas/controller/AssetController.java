package com.aivideo.canvas.controller;

import com.aivideo.canvas.common.BaseResponse;
import com.aivideo.canvas.dto.AssetDtos;
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

    @GetMapping("/{assetId}")
    public BaseResponse<Asset> getAsset(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long assetId
    ) {
        Long userId = authService.me(authorization).getId();
        return BaseResponse.ok(assetService.getAsset(userId, assetId));
    }

    @PostMapping("/reverse-prompt")
    public BaseResponse<AssetDtos.ReversePromptData> reversePrompt(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AssetDtos.ReversePromptReq req
    ) {
        Long userId = authService.me(authorization).getId();
        return BaseResponse.ok(assetService.reversePrompt(userId, req.getAssetId(), req.getHint()));
    }

    @PostMapping("/prompt-polish")
    public BaseResponse<AssetDtos.PromptPolishData> promptPolish(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AssetDtos.PromptPolishReq req
    ) {
        authService.me(authorization);
        return BaseResponse.ok(assetService.polishPrompt(req.getText(), req.getModel(), req.getStyleHint()));
    }

    @PostMapping("/generate-image")
    public BaseResponse<AssetDtos.GenerateImageData> generateImage(
            @RequestHeader("Authorization") String authorization,
            @RequestBody AssetDtos.GenerateImageReq req,
            @RequestParam(value = "project_id", required = false) Long projectId
    ) {
        Long userId = authService.me(authorization).getId();
        return BaseResponse.ok(assetService.generateImage(userId, projectId, req));
    }
}
