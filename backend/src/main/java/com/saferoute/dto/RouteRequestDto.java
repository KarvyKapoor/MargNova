package com.saferoute.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RouteRequestDto(
        @NotBlank(message = "Origin is required.")
        String origin,

        @NotBlank(message = "Destination is required.")
        String destination,

        @Pattern(regexp = "balanced|safety|heat|quick",
                 message = "Profile must be one of: balanced, safety, heat, quick.")
        String profile,

        @DecimalMin(value = "-10.0", message = "Temperature must be at least -10°C.")
        @DecimalMax(value = "60.0", message = "Temperature must not exceed 60°C.")
        Double temperature_c,

        @DecimalMin(value = "0.5", message = "Heat sensitivity must be between 0.5 and 2.0.")
        @DecimalMax(value = "2.0", message = "Heat sensitivity must between 0.5 and 2.0.")
        Double heat_sensitivity,

        @DecimalMin(value = "2.0", message = "Walking speed must be between 2.0 and 7.0 km/h.")
        @DecimalMax(value = "7.0", message = "Walking speed must be between 2.0 and 7.0 km/h.")
        Double walking_speed_kmh,

        Double originLat,
        Double originLng,
        Double destLat,
        Double destLng
) {
    public RouteRequestDto(String origin, String destination, String profile, Double temperature_c, Double heat_sensitivity, Double walking_speed_kmh) {
        this(origin, destination, profile, temperature_c, heat_sensitivity, walking_speed_kmh, null, null, null, null);
    }
}
