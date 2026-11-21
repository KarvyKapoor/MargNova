package com.saferoute.dto;

import java.time.Instant;
import java.util.Map;

public record HealthResponseDto(
        String status,
        String service,
        Instant timestamp,
        Map<String, Object> database,
        Object intelligence,
        Map<String, Object> weather
) {}

