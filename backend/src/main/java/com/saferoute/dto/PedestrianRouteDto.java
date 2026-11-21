package com.saferoute.dto;

import java.util.List;

public record PedestrianRouteDto(
        String id,
        String name,
        List<List<Double>> coordinates,
        Double distanceMeters,
        Double durationMinutes,
        List<StepInstructionDto> steps
) {}

