package com.saferoute.dto;

import java.util.List;
import java.util.Map;

public record RouteAlternativeDto(
        String id,
        String name,
        List<List<Double>> coordinates,
        List<String> node_ids,
        Map<String, Object> metrics,
        Double score,
        Double suitability_score,
        Map<String, Double> breakdown,
        Map<String, String> data_trust,
        List<String> reasons,
        List<Map<String, Object>> cooling_stops,
        Boolean recommended
) {}

