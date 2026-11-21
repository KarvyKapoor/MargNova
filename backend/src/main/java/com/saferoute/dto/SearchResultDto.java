package com.saferoute.dto;

public record SearchResultDto(
        String name,
        String displayName,
        Double lat,
        Double lng,
        String city,
        String state,
        String country,
        String type
) {}

