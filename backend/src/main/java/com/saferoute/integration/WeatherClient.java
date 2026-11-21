package com.saferoute.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.dto.DataTrustLevel;
import com.saferoute.dto.WeatherResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);

    private final WebClient webClient;
    private final String weatherUrl;
    private final ObjectMapper objectMapper;

    // 5-minute memory cache bucketed by rounded coordinates
    private final Map<String, CachedWeather> weatherCache = new ConcurrentHashMap<>();
    private static final Duration CACHE_DURATION = Duration.ofMinutes(5);

    public WeatherClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${saferoute.weather-url:https://api.open-meteo.com/v1/forecast}") String weatherUrl) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.weatherUrl = weatherUrl;
    }

    public WeatherResponseDto getWeather(double lat, double lng) {
        String cacheKey = String.format("%.2f_%.2f", lat, lng);
        CachedWeather cached = weatherCache.get(cacheKey);
        if (cached != null && Duration.between(cached.timestamp(), Instant.now()).compareTo(CACHE_DURATION) < 0) {
            log.debug("Returning cached Open-Meteo weather for coordinates: [{}, {}]", lat, lng);
            return cached.data();
        }

        try {
            String uri = String.format(
                    "%s?latitude=%.4f&longitude=%.4f&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,cloud_cover,uv_index,direct_normal_irradiance",
                    weatherUrl, lat, lng
            );

            String responseBody = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(5000))
                    .block();

            WeatherResponseDto dto = parseWeatherResponse(responseBody, lat, lng);
            weatherCache.put(cacheKey, new CachedWeather(Instant.now(), dto));
            return dto;
        } catch (Exception ex) {
            log.warn("Failed to fetch live weather from Open-Meteo ({}): {}. Returning graceful fallback.", weatherUrl, ex.getMessage());
            return new WeatherResponseDto(
                    lat,
                    lng,
                    32.0,
                    34.0,
                    45.0,
                    7.5,
                    0,
                    "Clear sky (Simulated Baseline)",
                    3.5,
                    20.0,
                    250.0,
                    DataTrustLevel.SIMULATED,
                    "Fallback Environmental Profile",
                    Instant.now(),
                    "Live weather provider unavailable. Operating with simulated baseline environmental indicators."
            );
        }
    }

    public Map<String, Object> checkStatus() {
        try {
            WeatherResponseDto test = getWeather(23.2599, 77.4126);
            return Map.of(
                    "status", test.dataTrust() == DataTrustLevel.VERIFIED ? "UP" : "DEGRADED",
                    "provider", test.provider(),
                    "trust", test.dataTrust().name()
            );
        } catch (Exception ex) {
            return Map.of("status", "DOWN", "error", ex.getMessage());
        }
    }

    private WeatherResponseDto parseWeatherResponse(String jsonString, double lat, double lng) throws Exception {
        JsonNode root = objectMapper.readTree(jsonString);
        JsonNode current = root.path("current");

        double temp = current.path("temperature_2m").asDouble(30.0);
        double apparentTemp = current.path("apparent_temperature").asDouble(temp);
        double humidity = current.path("relative_humidity_2m").asDouble(50.0);
        double wind = current.path("wind_speed_10m").asDouble(5.0);
        int code = current.path("weather_code").asInt(0);
        double uvIndex = current.path("uv_index").asDouble(3.5);
        double cloudCover = current.path("cloud_cover").asDouble(20.0);
        double radiation = current.path("direct_normal_irradiance").asDouble(250.0);

        String condition = mapWeatherCodeToCondition(code);

        return new WeatherResponseDto(
                lat,
                lng,
                temp,
                apparentTemp,
                humidity,
                wind,
                code,
                condition,
                uvIndex,
                cloudCover,
                radiation,
                DataTrustLevel.VERIFIED,
                "Open-Meteo",
                Instant.now(),
                WeatherResponseDto.WEATHER_DISCLAIMER
        );
    }

    private String mapWeatherCodeToCondition(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snowfall";
            case 80, 81, 82 -> "Rain showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Partly cloudy";
        };
    }

    private record CachedWeather(Instant timestamp, WeatherResponseDto data) {}
}
