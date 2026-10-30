package dev.liuwenjie.assetstudio.common;

/**
 * 业务异常：携带错误码，可选指向具体字段，由 GlobalExceptionHandler 转成统一响应体。
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ApiException(ErrorCode code, String message, String field) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }
}