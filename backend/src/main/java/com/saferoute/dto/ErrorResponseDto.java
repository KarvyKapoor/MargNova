package com.saferoute.dto;

import java.time.Instant;

public record ErrorResponseDto(
        String requestId,
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {}

