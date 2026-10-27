package com.ghoomo.ai;

/** A provider call failed. The router reacts by trying the next provider. */
public class LlmException extends RuntimeException {

    private final int httpStatus;

    public LlmException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = -1;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean isRateLimit() {
        return httpStatus == 429;
    }
}
