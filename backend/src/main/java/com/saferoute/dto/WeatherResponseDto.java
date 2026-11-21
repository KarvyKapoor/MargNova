package com.saferoute.dto;

import java.time.Instant;

public record WeatherResponseDto(
        Double latitude,
        Double longitude,
        Double temperatureC,
        Double apparentTemperatureC,
        Double relativeHumidityPct,
        Double windSpeedKmh,
        Integer weatherCode,
        String condition,
        Double uvIndex,
        Double cloudCoverPct,
        Double solarRadiation,
        DataTrustLevel dataTrust,
        String provider,
        Instant fetchedAt,
        String disclaimer
) {
    public static final String WEATHER_DISCLAIMER =
            "Live environmental weather data retrieved from Open-Meteo. Microclimate variations may exist.";
}
