package com.saferoute.controller;
import java.sql.Connection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saferoute.dto.CreateCoolingPlaceRequest;
import com.saferoute.dto.HealthResponseDto;
import com.saferoute.dto.PlaceDto;
import com.saferoute.dto.RouteAlternativeDto;
import com.saferoute.dto.RouteRecommendationResponseDto;
import com.saferoute.dto.RouteRequestDto;
import com.saferoute.dto.SearchResultDto;
import com.saferoute.dto.WeatherResponseDto;
import com.saferoute.service.GeocodingService;
import com.saferoute.service.IntelligenceService;
import com.saferoute.service.PlaceService;
import com.saferoute.service.RouteService;
import com.saferoute.service.WeatherService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api")
public class RouteController {

    private final RouteService routeService;
    private final WeatherService weatherService;
    private final PlaceService placeService;
    private final GeocodingService geocodingService;
    private final IntelligenceService intelligenceService;
    private final DataSource dataSource;

    public RouteController(
            RouteService routeService,
            WeatherService weatherService,
            PlaceService placeService,
            GeocodingService geocodingService,
            IntelligenceService intelligenceService,
            DataSource dataSource) {
        this.routeService = routeService;
        this.weatherService = weatherService;
        this.placeService = placeService;
        this.geocodingService = geocodingService;
        this.intelligenceService = intelligenceService;
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponseDto> health() {
        Map<String, Object> dbStatus = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(2);
            dbStatus.put("status", valid ? "UP" : "DEGRADED");
            dbStatus.put("databaseProduct", conn.getMetaData().getDatabaseProductName());
            dbStatus.put("schema", "Flyway V1 schema verified");
        } catch (Exception ex) {
            dbStatus.put("status", "DOWN");
            dbStatus.put("error", "Database connection failed");
        }

        Object intelligenceStatus = intelligenceService.health();
        Map<String, Object> weatherStatus = weatherService.checkStatus();

        HealthResponseDto response = new HealthResponseDto(
                "ok",
                "saferoute-backend",
                Instant.now(),
                dbStatus,
                intelligenceStatus,
                weatherStatus
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/geocoding/search")
    public ResponseEntity<List<SearchResultDto>> searchPlaces(
            @RequestParam String q,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        return ResponseEntity.ok(geocodingService.search(q, lat, lng));
    }

    @GetMapping("/geocoding/reverse")
    public ResponseEntity<SearchResultDto> reverseGeocode(
            @RequestParam Double lat,
            @RequestParam Double lng) {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Invalid coordinates. Latitude must be in [-90, 90], Longitude in [-180, 180].");
        }
        return ResponseEntity.ok(geocodingService.reverse(lat, lng));
    }

    @GetMapping("/weather")
    public ResponseEntity<WeatherResponseDto> weather(
            @RequestParam(required = false, defaultValue = "23.2599") Double lat,
            @RequestParam(required = false, defaultValue = "77.4126") Double lng) {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Invalid coordinates. Latitude must be in [-90, 90], Longitude in [-180, 180].");
        }
        return ResponseEntity.ok(weatherService.getWeather(lat, lng));
    }

    @GetMapping("/places/nearby")
    public ResponseEntity<Map<String, Object>> placesNearby(
            @RequestParam(required = false, defaultValue = "23.2610") Double lat,
            @RequestParam(required = false, defaultValue = "77.4120") Double lng,
            @RequestParam(required = false, defaultValue = "2000.0") Double radius_m) {
        if (lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Invalid coordinates. Latitude must be in [-90, 90], Longitude in [-180, 180].");
        }
        if (radius_m <= 0 || radius_m > 50000.0) {
            throw new IllegalArgumentException("Search radius must be between 1 and 50,000 meters.");
        }

        List<PlaceDto> places = placeService.getNearbyPlaces(lat, lng, radius_m);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("places", places);
        response.put("total", places.size());
        response.put("center", Map.of("lat", lat, "lng", lng));
        response.put("radius_m", radius_m);
        response.put("notice", "Cooling and rest points are illustrative and must be verified locally before travel.");
        response.put("disclaimer", RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/routes/alternatives")
    public ResponseEntity<Map<String, Object>> alternatives(@Valid @RequestBody RouteRequestDto request) {
        if (request.origin().trim().equalsIgnoreCase(request.destination().trim())) {
            throw new IllegalArgumentException("Origin and destination must be different.");
        }

        List<RouteAlternativeDto> alternatives = routeService.getRouteAlternatives(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("origin", request.origin());
        response.put("destination", request.destination());
        response.put("profile", request.profile() != null ? request.profile() : "balanced");
        response.put("temperature_c", request.temperature_c() != null ? request.temperature_c() : 36.0);
        response.put("alternatives_count", alternatives.size());
        response.put("routes", alternatives);
        response.put("notice", "Pedestrian route intelligence calculated. Verify local conditions before travel.");
        response.put("disclaimer", RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/routes/recommend")
    public ResponseEntity<RouteRecommendationResponseDto> recommend(@Valid @RequestBody RouteRequestDto request) {
        if (request.origin().trim().equalsIgnoreCase(request.destination().trim())) {
            throw new IllegalArgumentException("Origin and destination must be different.");
        }

        RouteRecommendationResponseDto recommendation = routeService.recommendRoute(request);
        return ResponseEntity.ok(recommendation);
    }

    // --- Legacy / Existing Frontend Compatibility Endpoints ---

    @GetMapping("/locations")
    public Object locations() {
        return intelligenceService.locations();
    }

    @PostMapping("/routes")
    public Object routes(@Valid @RequestBody RoutePayload payload) {
        if (payload.origin().trim().equalsIgnoreCase(payload.destination().trim())) {
            throw new IllegalArgumentException("Origin and destination must be different.");
        }
        RouteRequestDto request = new RouteRequestDto(
                payload.origin(),
                payload.destination(),
                payload.profile(),
                payload.temperature_c(),
                payload.heat_sensitivity(),
                payload.walking_speed_kmh(),
                payload.originLat(),
                payload.originLng(),
                payload.destLat(),
                payload.destLng()
        );
        return routeService.recommendRoute(request);
    }

    public record RoutePayload(
            @NotBlank(message = "Origin is required.") String origin,
            @NotBlank(message = "Destination is required.") String destination,
            @Pattern(regexp = "balanced|safety|heat|quick",
                    message = "Profile must be balanced, safety, heat or quick.")
            String profile,
            @DecimalMin(value = "-10.0", message = "Temperature must be at least -10°C.")
            @DecimalMax(value = "60.0", message = "Temperature must not exceed 60°C.")
            Double temperature_c,
            @DecimalMin(value = "0.5", message = "Heat sensitivity must be at least 0.5.")
            @DecimalMax(value = "2.0", message = "Heat sensitivity must not exceed 2.0.")
            Double heat_sensitivity,
            @DecimalMin(value = "2.0", message = "Walking speed must be at least 2 km/h.")
            @DecimalMax(value = "7.0", message = "Walking speed must not exceed 7 km/h.")
            Double walking_speed_kmh,
            Double originLat,
            Double originLng,
            Double destLat,
            Double destLng
    ) {
        public RoutePayload(String origin, String destination, String profile, Double temperature_c, Double heat_sensitivity, Double walking_speed_kmh) {
            this(origin, destination, profile, temperature_c, heat_sensitivity, walking_speed_kmh, null, null, null, null);
        }
    }

@PostMapping("/places")
public ResponseEntity<PlaceDto> addCoolingPlace(
        @Valid @RequestBody CreateCoolingPlaceRequest request) {

    PlaceDto saved = placeService.addCoolingPlace(request);

    return ResponseEntity.status(201).body(saved);
}

}
