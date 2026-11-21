package com.saferoute.service;

import com.saferoute.dto.*;
import com.saferoute.entity.LocationNodeEntity;
import com.saferoute.entity.RouteEvaluationEntity;
import com.saferoute.integration.GeocodingClient;
import com.saferoute.integration.PedestrianRoutingClient;
import com.saferoute.integration.RouteIntelligenceClient;
import com.saferoute.integration.WeatherClient;
import com.saferoute.repository.LocationNodeRepository;
import com.saferoute.repository.RouteEvaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RouteService {

    private static final Logger log = LoggerFactory.getLogger(RouteService.class);

    private final RouteIntelligenceClient intelligenceClient;
    private final PedestrianRoutingClient routingClient;
    private final GeocodingClient geocodingClient;
    private final WeatherClient weatherClient;
    private final RouteEvaluationRepository evaluationRepository;
    private final LocationNodeRepository locationNodeRepository;

    public RouteService(
            RouteIntelligenceClient intelligenceClient,
            PedestrianRoutingClient routingClient,
            GeocodingClient geocodingClient,
            WeatherClient weatherClient,
            RouteEvaluationRepository evaluationRepository,
            LocationNodeRepository locationNodeRepository) {
        this.intelligenceClient = intelligenceClient;
        this.routingClient = routingClient;
        this.geocodingClient = geocodingClient;
        this.weatherClient = weatherClient;
        this.evaluationRepository = evaluationRepository;
        this.locationNodeRepository = locationNodeRepository;
    }

    public RouteRecommendationResponseDto recommendRoute(RouteRequestDto request) {
        // 1. Resolve geographic start and end coordinates
        double[] start = resolveCoordinates(request.origin(), request.originLat(), request.originLng());
        double[] end = resolveCoordinates(request.destination(), request.destLat(), request.destLng());

        if (start != null && end != null) {
            return processRealWorldRoute(request, start[0], start[1], end[0], end[1]);
        }

        // 2. Check if the request explicitly targets valid demo graph node IDs
        Optional<LocationNodeEntity> startNode = locationNodeRepository.findById(request.origin().trim().toLowerCase());
        Optional<LocationNodeEntity> endNode = locationNodeRepository.findById(request.destination().trim().toLowerCase());
        if (startNode.isPresent() && endNode.isPresent()) {
            RouteRecommendationResponseDto response = intelligenceClient.evaluateRoutes(request);
            persistAuditRecord(request, response);
            return response;
        }

        // 3. Otherwise report exact unresolved location clearly
        String unresolvable = (start == null ? request.origin() : request.destination());
        throw new IllegalArgumentException(
                "Unable to resolve geographical coordinates for location: '" + unresolvable +
                "'. Please provide a valid address text, coordinates (lat,lng), or select from search suggestions."
        );
    }

    public List<RouteAlternativeDto> getRouteAlternatives(RouteRequestDto request) {
        RouteRecommendationResponseDto response = recommendRoute(request);
        return response.routes();
    }

    private RouteRecommendationResponseDto processRealWorldRoute(
            RouteRequestDto request, double startLat, double startLng, double endLat, double endLng) {
        // 1. Fetch live Open-Meteo weather at route origin
        WeatherResponseDto weather = weatherClient.getWeather(startLat, startLng);

        // 2. Fetch actual pedestrian walking routes from routing provider (ORS / OSRM)
        List<PedestrianRouteDto> rawRoutes = routingClient.getPedestrianRoutes(startLat, startLng, endLat, endLng);
        if (rawRoutes.isEmpty()) {
            throw new IllegalArgumentException("No walkable pedestrian path found between the specified locations.");
        }

        // 3. Evaluate routes with Python geospatial & heat exposure intelligence
        RouteRecommendationResponseDto evaluation = intelligenceClient.evaluateRealRoutes(
                request.origin(),
                request.destination(),
                List.of(startLat, startLng),
                List.of(endLat, endLng),
                rawRoutes,
                weather,
                request.profile(),
                request.heat_sensitivity(),
                request.walking_speed_kmh()
        );

        persistAuditRecord(request, evaluation);
        return evaluation;
    }

    private double[] resolveCoordinates(String loc, Double explicitLat, Double explicitLng) {
        if (explicitLat != null && explicitLng != null) {
            return new double[]{explicitLat, explicitLng};
        }
        if (loc == null || loc.isBlank()) {
            return null;
        }

        // 1. Try parsing "lat,lng" format
        if (loc.contains(",")) {
            try {
                String[] parts = loc.split(",");
                if (parts.length == 2) {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lng = Double.parseDouble(parts[1].trim());
                    if (lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0) {
                        return new double[]{lat, lng};
                    }
                }
            } catch (Exception ignored) {}
        }

        // 2. Check local database demo nodes (e.g. station, library, park)
        Optional<LocationNodeEntity> node = locationNodeRepository.findById(loc.trim().toLowerCase());
        if (node.isPresent()) {
            return new double[]{node.get().getLat(), node.get().getLng()};
        }

        // 3. Check demo aliases and common landmarks
        double[] aliasCoords = resolveDemoAlias(loc.trim());
        if (aliasCoords != null) {
            return aliasCoords;
        }

        // 4. Geocode using Photon / OSM
        List<SearchResultDto> results = geocodingClient.searchPlaces(loc, 23.2599, 77.4126);
        if (!results.isEmpty()) {
            return new double[]{results.get(0).lat(), results.get(0).lng()};
        }

        return null;
    }

    private double[] resolveDemoAlias(String loc) {
        String lower = loc.toLowerCase();
        if (lower.contains("station") || lower.contains("transit hub") || lower.contains("bhopal junction")) {
            return new double[]{23.2599, 77.4126};
        }
        if (lower.contains("library") || lower.contains("civic library")) {
            return new double[]{23.2630, 77.4134};
        }
        if (lower.contains("park") || lower.contains("central green")) {
            return new double[]{23.2620, 77.4110};
        }
        if (lower.contains("market") || lower.contains("market crossing")) {
            return new double[]{23.2610, 77.4142};
        }
        if (lower.contains("clinic") || lower.contains("hospital")) {
            return new double[]{23.2578, 77.4144};
        }
        if (lower.contains("lake") || lower.contains("lake walk") || lower.contains("upper lake")) {
            return new double[]{23.2600, 77.4092};
        }
        return null;
    }

    private void persistAuditRecord(RouteRequestDto request, RouteRecommendationResponseDto response) {
        try {
            RouteAlternativeDto recommended = response.recommended_route();
            Double suitability = recommended != null ? recommended.suitability_score() : null;
            Double heat = null;
            Double safety = null;
            Double duration = null;
            Integer distance = null;

            if (recommended != null && recommended.metrics() != null) {
                Object heatObj = recommended.metrics().get("heat");
                if (heatObj instanceof Number n) heat = n.doubleValue();

                Object durObj = recommended.metrics().get("duration_min");
                if (durObj instanceof Number n) duration = n.doubleValue();

                Object distObj = recommended.metrics().get("distance_m");
                if (distObj instanceof Number n) distance = n.intValue();

                Object hazardObj = recommended.metrics().get("hazard");
                if (hazardObj instanceof Number n) safety = 100.0 - (n.doubleValue() * 100.0);
            }

            RouteEvaluationEntity audit = new RouteEvaluationEntity(
                    UUID.randomUUID().toString(),
                    request.origin(),
                    request.destination(),
                    request.profile() != null ? request.profile() : "balanced",
                    request.temperature_c() != null ? request.temperature_c() : 36.0,
                    recommended != null ? recommended.id() : null,
                    suitability,
                    heat,
                    safety,
                    duration,
                    distance
            );

            evaluationRepository.save(audit);
        } catch (Exception ex) {
            log.warn("Non-critical failure logging route evaluation audit: {}", ex.getMessage());
        }
    }
}
