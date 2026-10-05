package com.playwrightforkubejs.protocol;

public final class PlaywrightException extends RuntimeException {
    private final ErrorCode code;

    public PlaywrightException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public PlaywrightException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getCodeName() {
        return code.name();
    }
}
