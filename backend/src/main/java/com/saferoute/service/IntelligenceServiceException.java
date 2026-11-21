package com.saferoute.service;

import org.springframework.http.HttpStatusCode;

public class IntelligenceServiceException extends RuntimeException {
    private final HttpStatusCode status;

    public IntelligenceServiceException(HttpStatusCode status, String message) {
        super(message);
        this.status = status;
    }

    public IntelligenceServiceException(HttpStatusCode status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}