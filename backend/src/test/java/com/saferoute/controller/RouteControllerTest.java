package com.saferoute.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.dto.*;
import com.saferoute.service.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RouteService routeService;

    @MockitoBean
    private WeatherService weatherService;

    @MockitoBean
    private PlaceService placeService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private IntelligenceService intelligenceService;

    @Test
    @DisplayName("GET /api/health should return UP status and component health")
    void testHealthEndpoint() throws Exception {
        Mockito.when(intelligenceService.health()).thenReturn(Map.of("status", "ok", "service", "saferoute-python"));
        Mockito.when(weatherService.checkStatus()).thenReturn(Map.of("status", "UP", "provider", "Open-Meteo"));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.service").value("saferoute-backend"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.weather.status").value("UP"));
    }

    @Test
    @DisplayName("GET /api/geocoding/search should return matching addresses")
    void testGeocodingSearch() throws Exception {
        SearchResultDto place = new SearchResultDto(
                "Van Vihar National Park", "Van Vihar National Park, Shymala Hills, Bhopal",
                23.2300, 77.3600, "Bhopal", "Madhya Pradesh", "India", "park"
        );
        Mockito.when(geocodingService.search(anyString(), any(), any())).thenReturn(List.of(place));

        mockMvc.perform(get("/api/geocoding/search").param("q", "Van Vihar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Van Vihar National Park"))
                .andExpect(jsonPath("$[0].lat").value(23.2300))
                .andExpect(jsonPath("$[0].lng").value(77.3600));
    }

    @Test
    @DisplayName("GET /api/geocoding/reverse should reverse geocode coordinates")
    void testGeocodingReverse() throws Exception {
        SearchResultDto place = new SearchResultDto(
                "Bhopal Junction", "Bhopal Junction, Railway Station Road, Bhopal",
                23.2599, 77.4126, "Bhopal", "Madhya Pradesh", "India", "station"
        );
        Mockito.when(geocodingService.reverse(anyDouble(), anyDouble())).thenReturn(place);

        mockMvc.perform(get("/api/geocoding/reverse").param("lat", "23.2599").param("lng", "77.4126"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bhopal Junction"));
    }

    @Test
    @DisplayName("GET /api/weather should return live or cached environmental conditions")
    void testWeatherEndpoint() throws Exception {
        WeatherResponseDto sampleWeather = new WeatherResponseDto(
                23.2599, 77.4126, 34.5, 36.0, 42.0, 7.5, 0,
                "Clear sky", 4.5, 10.0, 300.0, DataTrustLevel.VERIFIED, "Open-Meteo",
                Instant.now(), WeatherResponseDto.WEATHER_DISCLAIMER
        );
        Mockito.when(weatherService.getWeather(anyDouble(), anyDouble())).thenReturn(sampleWeather);

        mockMvc.perform(get("/api/weather").param("lat", "23.2599").param("lng", "77.4126"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temperatureC").value(34.5))
                .andExpect(jsonPath("$.condition").value("Clear sky"))
                .andExpect(jsonPath("$.uvIndex").value(4.5))
                .andExpect(jsonPath("$.dataTrust").value("VERIFIED"));
    }

    @Test
    @DisplayName("GET /api/places/nearby should return cooling locations with data trust label")
    void testNearbyPlaces() throws Exception {
        PlaceDto place = new PlaceDto(
                "park", "Central Green Rest Point", "PARK",
                23.2620, 77.4110, 0.82, false, "06:00 - 20:00",
                "Tree canopy and seating", DataTrustLevel.SIMULATED, 250.0
        );
        Mockito.when(placeService.getNearbyPlaces(any(), any(), any())).thenReturn(List.of(place));

        mockMvc.perform(get("/api/places/nearby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.places[0].name").value("Central Green Rest Point"))
                .andExpect(jsonPath("$.places[0].dataTrust").value("SIMULATED"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    @DisplayName("POST /api/routes/recommend should return optimal route and explanation")
    void testRecommendRoute() throws Exception {
        RouteAlternativeDto alt = new RouteAlternativeDto(
                "route-1", "Balanced alternative",
                List.of(List.of(23.2599, 77.4126), List.of(23.2630, 77.4134)),
                List.of("station", "library"),
                Map.of("distance_m", 470, "duration_min", 6.3, "heat", 28.0),
                32.5, 67.5,
                Map.of("heat", 6.7, "duration", 10.0),
                Map.of("heat", "ESTIMATED"),
                List.of("Estimated heat burden is lower"),
                List.of(), true
        );

        RouteRecommendationResponseDto resp = new RouteRecommendationResponseDto(
                "station", "library", "balanced", 36.0,
                List.of(alt), alt, "Demo notice", RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER
        );

        Mockito.when(routeService.recommendRoute(any())).thenReturn(resp);

        RouteRequestDto req = new RouteRequestDto(
                "station", "library", "balanced", 36.0, 1.0, 4.5
        );

        mockMvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("station"))
                .andExpect(jsonPath("$.destination").value("library"))
                .andExpect(jsonPath("$.recommended_route.id").value("route-1"))
                .andExpect(jsonPath("$.disclaimer").value(RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER));
    }

    @Test
    @DisplayName("POST /api/routes/recommend should reject identical origin and destination")
    void testSameOriginDestinationRejected() throws Exception {
        RouteRequestDto req = new RouteRequestDto(
                "station", "station", "balanced", 36.0, 1.0, 4.5
        );

        mockMvc.perform(post("/api/routes/recommend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Origin and destination must be different."));
    }

    @Test
    @DisplayName("Spring Security should deny unmapped endpoints")
    void testSecurityDeniesUnmapped() throws Exception {
        mockMvc.perform(get("/api/unmapped-secret-path"))
                .andExpect(status().isForbidden());
    }
}
