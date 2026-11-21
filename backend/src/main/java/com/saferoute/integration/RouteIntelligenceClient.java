package com.saferoute.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.dto.*;
import com.saferoute.service.IntelligenceServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;

@Component
public class RouteIntelligenceClient {

    private static final Logger log = LoggerFactory.getLogger(RouteIntelligenceClient.class);

    private final WebClient webClient;
    private final String baseUrl;
    private final String internalKey;
    private final ObjectMapper objectMapper;

    public RouteIntelligenceClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${saferoute.intelligence-url:http://localhost:8000}") String baseUrl,
            @Value("${saferoute.intelligence-key:dev-internal-secret-key-saferoute-2026}") String internalKey) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalKey = internalKey;
    }

    public Map<String, Object> checkHealth() {
        try {
            return webClient.get()
                    .uri(baseUrl + "/health")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(3000))
                    .block();
        } catch (Exception ex) {
            log.warn("Health check failed for Python intelligence service at {}: {}", baseUrl, ex.getMessage());
            return Map.of("status", "DOWN", "error", "Python intelligence service unavailable at " + baseUrl);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getLocations() {
        String uri = baseUrl + "/api/v1/locations";
        try {
            return webClient.get()
                    .uri(uri)
                    .header("X-Internal-Service-Key", internalKey)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofMillis(5000))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(300))
                            .filter(this::isTransientError))
                    .block();
        } catch (WebClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String error = extractErrorMessage(responseBody, "Error retrieving locations from intelligence service.");
            log.error("[Python Locations Error] Endpoint: {} | Status: {} | Body: {} | Error: {}",
                    uri, ex.getStatusCode(), responseBody, error);
            throw new IntelligenceServiceException(ex.getStatusCode(), error, ex);
        } catch (Exception ex) {
            throw new IntelligenceServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Python intelligence service is unavailable. Please verify FastAPI is running on port 8000.", ex);
        }
    }

    public RouteRecommendationResponseDto evaluateRoutes(RouteRequestDto request) {
        String uri = baseUrl + "/api/v1/routes";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin", request.origin().trim());
        body.put("destination", request.destination().trim());
        body.put("profile", request.profile() == null ? "balanced" : request.profile());
        body.put("temperature_c", request.temperature_c() == null ? 36.0 : request.temperature_c());
        body.put("heat_sensitivity", request.heat_sensitivity() == null ? 1.0 : request.heat_sensitivity());
        body.put("walking_speed_kmh", request.walking_speed_kmh() == null ? 4.5 : request.walking_speed_kmh());

        String requestJson = "";
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (Exception ignored) {}

        log.info("Dispatching evaluateRoutes to Python: POST {} | Payload: {}", uri, requestJson);

        try {
            String rawJson = webClient.post()
                    .uri(uri)
                    .header("X-Internal-Service-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(8000))
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(400))
                            .filter(this::isTransientError))
                    .block();

            return parseRecommendationResponse(rawJson, request);
        } catch (WebClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String errorMsg = extractErrorMessage(responseBody, "Python intelligence service rejected the route evaluation request.");
            log.error("""
                    [Python evaluateRoutes HTTP Error]
                    Endpoint: {}
                    Status: {} ({})
                    Request JSON: {}
                    Response Body: {}
                    Parsed Error: {}""",
                    uri, ex.getStatusCode().value(), ex.getStatusCode(), requestJson, responseBody, errorMsg);
            throw new IntelligenceServiceException(ex.getStatusCode(), errorMsg, ex);
        } catch (WebClientRequestException ex) {
            log.error("Cannot connect to Python intelligence service at {}: {}", uri, ex.getMessage());
            throw new IntelligenceServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cannot connect to Python intelligence service at " + baseUrl + ". Verify that the FastAPI service is running.",
                    ex
            );
        } catch (Exception ex) {
            if (ex instanceof IntelligenceServiceException ise) throw ise;
            log.error("Failed to communicate with route intelligence service: {}", ex.getMessage(), ex);
            throw new IntelligenceServiceException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to communicate with route intelligence service: " + ex.getMessage(),
                    ex
            );
        }
    }

    public RouteRecommendationResponseDto evaluateRealRoutes(
            String originName,
            String destinationName,
            List<Double> originCoords,
            List<Double> destCoords,
            List<PedestrianRouteDto> routes,
            WeatherResponseDto weather,
            String profile,
            Double heatSensitivity,
            Double walkingSpeed) {

        String uri = baseUrl + "/api/v1/evaluate-real-routes";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("origin_name", originName);
        body.put("destination_name", destinationName);
        body.put("origin_coords", originCoords);
        body.put("destination_coords", destCoords);

        List<Map<String, Object>> routeItems = new ArrayList<>();
        for (PedestrianRouteDto r : routes) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", r.id());
            item.put("name", r.name());
            item.put("coordinates", r.coordinates());
            item.put("distance_m", r.distanceMeters());
            item.put("duration_min", r.durationMinutes());
            item.put("steps", r.steps());
            routeItems.add(item);
        }
        body.put("routes", routeItems);

        Map<String, Object> weatherMap = new LinkedHashMap<>();
        weatherMap.put("temperature_c", weather.temperatureC());
        weatherMap.put("apparent_temperature_c", weather.apparentTemperatureC());
        weatherMap.put("relative_humidity_pct", weather.relativeHumidityPct());
        weatherMap.put("wind_speed_kmh", weather.windSpeedKmh());
        weatherMap.put("weather_code", weather.weatherCode());
        weatherMap.put("condition", weather.condition());
        weatherMap.put("uv_index", weather.uvIndex());
        weatherMap.put("cloud_cover_pct", weather.cloudCoverPct());
        weatherMap.put("solar_radiation", weather.solarRadiation());
        body.put("weather", weatherMap);

        body.put("profile", profile != null ? profile : "balanced");
        body.put("heat_sensitivity", heatSensitivity != null ? heatSensitivity : 1.0);
        body.put("walking_speed_kmh", walkingSpeed != null ? walkingSpeed : 4.5);

        String requestJson = "";
        try {
            requestJson = objectMapper.writeValueAsString(body);
        } catch (Exception ignored) {}

        log.info("Dispatching evaluateRealRoutes to Python: POST {} | Routes: {} | Profile: {}",
                uri, routes.size(), profile);

        try {
            String rawJson = webClient.post()
                    .uri(uri)
                    .header("X-Internal-Service-Key", internalKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(9000))
                    .block();

            return parseRecommendationResponse(rawJson, new RouteRequestDto(originName, destinationName, profile, weather.temperatureC(), heatSensitivity, walkingSpeed));
        } catch (WebClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String errorMsg = extractErrorMessage(responseBody, "Python intelligence service rejected real route evaluation.");
            log.error("""
                    [Python evaluateRealRoutes HTTP Error]
                    Endpoint: {}
                    Status: {} ({})
                    Request JSON: {}
                    Response Body: {}
                    Parsed Error: {}""",
                    uri, ex.getStatusCode().value(), ex.getStatusCode(), requestJson, responseBody, errorMsg);

            // Re-throw 4xx client errors without masking so invalid parameters/routes are identified
            throw new IntelligenceServiceException(ex.getStatusCode(), errorMsg, ex);
        } catch (WebClientRequestException ex) {
            log.warn("Cannot connect to Python intelligence service at {}: {}. Falling back to internal gateway estimation.", uri, ex.getMessage());
            return generateGatewayFallbackRecommendation(originName, destinationName, routes, weather, profile);
        } catch (Exception ex) {
            if (ex instanceof IntelligenceServiceException ise) throw ise;
            log.warn("Python evaluate-real-routes call failed: {}. Falling back to internal gateway estimation.", ex.getMessage());
            return generateGatewayFallbackRecommendation(originName, destinationName, routes, weather, profile);
        }
    }

    private RouteRecommendationResponseDto generateGatewayFallbackRecommendation(
            String originName, String destName, List<PedestrianRouteDto> routes, WeatherResponseDto weather, String profile) {
        List<RouteAlternativeDto> alternatives = new ArrayList<>();
        int idx = 1;
        for (PedestrianRouteDto r : routes) {
            double shade = idx == 2 ? 0.45 : 0.22;
            double thermalBase = Math.max(0.0, (weather.temperatureC() - 20.0) * 3.0);
            double heatBurden = Math.round(thermalBase * (1.0 - shade));

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("distance_m", r.distanceMeters().intValue());
            metrics.put("duration_min", r.durationMinutes());
            metrics.put("heat", heatBurden);
            metrics.put("heat_score", heatBurden);
            metrics.put("shade_coverage_pct", (int)(shade * 100));
            metrics.put("lighting", 0.35);
            metrics.put("crowd", 0.30);
            metrics.put("hazard", 0.15);
            metrics.put("cooling_access", idx == 2 ? 25.0 : 60.0);
            metrics.put("visibility", 0.75);

            double score = Math.round(heatBurden * 0.3 + r.durationMinutes() * 2.0);
            double suitability = Math.max(10.0, 100.0 - score);

            RouteAlternativeDto alt = new RouteAlternativeDto(
                    r.id(),
                    r.name(),
                    r.coordinates(),
                    List.of("start", "end"),
                    metrics,
                    score,
                    suitability,
                    Map.of("heat", heatBurden * 0.3, "duration", r.durationMinutes() * 2.0),
                    Map.of("heat", "ESTIMATED", "weather", "VERIFIED"),
                    List.of("Route evaluated using live Open-Meteo weather and pedestrian network path."),
                    List.of(),
                    idx == 1
            );
            alternatives.add(alt);
            idx++;
        }

        return new RouteRecommendationResponseDto(
                originName, destName, profile, weather.temperatureC(),
                alternatives, alternatives.isEmpty() ? null : alternatives.get(0),
                "Gateway estimation used (Python service connection unavailable).",
                RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER
        );
    }

    private RouteRecommendationResponseDto parseRecommendationResponse(String jsonString, RouteRequestDto request) {
        try {
            JsonNode root = objectMapper.readTree(jsonString);
            String origin = root.path("origin").asText(request.origin());
            String destination = root.path("destination").asText(request.destination());
            String profile = root.path("profile").asText(request.profile());
            double temp = root.path("temperature_c").asDouble(request.temperature_c() != null ? request.temperature_c() : 36.0);
            String notice = root.path("notice").asText("Pedestrian route intelligence calculated.");
            String disclaimer = root.path("disclaimer").asText(RouteRecommendationResponseDto.SAFE_ROUTE_DISCLAIMER);

            List<RouteAlternativeDto> alternatives = new ArrayList<>();
            JsonNode routesNode = root.path("routes");

            RouteAlternativeDto recommendedRoute = null;

            if (routesNode.isArray()) {
                for (JsonNode rNode : routesNode) {
                    RouteAlternativeDto alt = objectMapper.treeToValue(rNode, RouteAlternativeDto.class);
                    alternatives.add(alt);
                    if (Boolean.TRUE.equals(alt.recommended()) && recommendedRoute == null) {
                        recommendedRoute = alt;
                    }
                }
            }

            if (recommendedRoute == null && !alternatives.isEmpty()) {
                recommendedRoute = alternatives.get(0);
            }

            return new RouteRecommendationResponseDto(
                    origin,
                    destination,
                    profile,
                    temp,
                    alternatives,
                    recommendedRoute,
                    notice,
                    disclaimer
            );
        } catch (Exception ex) {
            log.error("Failed to parse route recommendation response: {}", ex.getMessage());
            throw new IntelligenceServiceException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid response structure received from Python intelligence service: " + ex.getMessage(), ex);
        }
    }

    private boolean isTransientError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return ex.getStatusCode().is5xxServerError();
        }
        return throwable instanceof WebClientRequestException;
    }

    private String extractErrorMessage(String responseBody, String defaultMessage) {
        if (responseBody == null || responseBody.isBlank()) return defaultMessage;
        try {
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode detail = json.get("detail");
            if (detail != null && detail.isTextual()) {
                return detail.asText();
            } else if (detail != null && detail.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode err : detail) {
                    if (!sb.isEmpty()) sb.append("; ");
                    String loc = "";
                    if (err.has("loc") && err.get("loc").isArray()) {
                        JsonNode locArr = err.get("loc");
                        loc = locArr.get(locArr.size() - 1).asText();
                    }
                    String msg = err.path("msg").asText("Validation error");
                    sb.append(loc.isBlank() ? msg : loc + ": " + msg);
                }
                return sb.toString();
            }
            if (json.hasNonNull("message")) {
                return json.get("message").asText();
            }
        } catch (Exception ignored) {}
        return responseBody.length() < 200 ? responseBody : defaultMessage;
    }
}
