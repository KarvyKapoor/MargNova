package com.saferoute.dto;

public record StepInstructionDto(
        String instruction,
        Double distanceMeters,
        Double durationSeconds,
        String streetName
) {}

