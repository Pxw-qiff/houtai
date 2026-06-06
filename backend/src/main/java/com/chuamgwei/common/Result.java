package com.chuamgwei.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一接口返回包装类
 */
@Data
public class Result<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private int code;
    private String message;
    private T data;

    public Result() {
        this.code = 200;
        this.message = "success";
    }

    public Result(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result(T data) {
        this.code = 200;
        this.message = "success";
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message);
    }
}
