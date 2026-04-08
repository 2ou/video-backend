package com.aivideo.canvas.common;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BaseResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> BaseResponse<T> ok(T data){
        return new BaseResponse<>("OK","success",data);
    }
}
