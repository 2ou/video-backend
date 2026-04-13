package com.aivideo.canvas.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor // 必须加，否则 Spring 序列化可能报错
public class BaseResponse<T> {
    private String code;
    private String message;
    private T data;

    // 报红就是因为 IDEA 当前没读到下面这个方法
    public static <T> BaseResponse<T> ok(T data){
        return new BaseResponse<>("OK", "success", data);
    }
}