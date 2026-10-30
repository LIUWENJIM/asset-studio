package dev.liuwenjie.assetstudio.common;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。HTTP 状态由这里统一决定，控制器只抛 ApiException。
 */
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "请求参数不合法"),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND, "模板不存在"),
    ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "素材不存在"),
    ASSET_NOT_OWNED(HttpStatus.BAD_REQUEST, "素材不属于当前用户"),
    ASSET_COUNT_MISMATCH(HttpStatus.BAD_REQUEST, "素材数量与模板要求不一致"),
    ASSET_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "不支持的图片类型"),
    ASSET_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "图片超过大小限制"),
    ASSET_EMPTY(HttpStatus.BAD_REQUEST, "上传文件为空"),
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "任务不存在"),
    UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "素材保存失败"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务内部错误");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}