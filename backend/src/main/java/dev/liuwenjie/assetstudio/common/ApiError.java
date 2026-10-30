package dev.liuwenjie.assetstudio.common;

/**
 * 统一错误响应体：{ code, message, field? }
 * 前端只依赖这三个字段做提示与字段级高亮。
 */
public record ApiError(String code, String message, String field) {

    public static ApiError of(ErrorCode code, String message, String field) {
        return new ApiError(code.name(), message, field);
    }
}