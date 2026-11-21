package com.saferoute.dto;

import java.util.List;

public record RouteRecommendationResponseDto(
        String origin,
        String destination,
        String profile,
        Double temperature_c,
        List<RouteAlternativeDto> routes,
        RouteAlternativeDto recommended_route,
        String notice,
        String disclaimer
) {
    public static final String SAFE_ROUTE_DISCLAIMER =
            "MargNova provides estimated route suitability information and does not guarantee personal safety.";
}

