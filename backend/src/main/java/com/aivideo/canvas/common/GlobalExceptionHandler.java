package com.aivideo.canvas.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AppException.class)
    public ResponseEntity<BaseResponse<Object>> handleApp(AppException ex){
        return ResponseEntity.badRequest().body(new BaseResponse<>(ex.getCode(), ex.getMessage(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Object>> handleAny(Exception ex){
        return ResponseEntity.internalServerError().body(new BaseResponse<>("INTERNAL_ERROR","Internal server error",null));
    }
}
