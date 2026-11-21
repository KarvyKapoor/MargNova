package com.saferoute.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.dto.PedestrianRouteDto;
import com.saferoute.dto.StepInstructionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PedestrianRoutingClient {

    private static final Logger log = LoggerFactory.getLogger(PedestrianRoutingClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String orsUrl;
    private final String orsApiKey;
    private final String osrmUrl;

    public PedestrianRoutingClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${saferoute.openrouteservice.url:https://api.openrouteservice.org}") String orsUrl,
            @Value("${saferoute.openrouteservice.api-key:}") String orsApiKey,
            @Value("${saferoute.osrm.url:https://routing.openstreetmap.de/routed-foot}") String osrmUrl) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.orsUrl = orsUrl;
        this.orsApiKey = orsApiKey;
        this.osrmUrl = osrmUrl;
    }

    public List<PedestrianRouteDto> getPedestrianRoutes(double startLat, double startLng, double endLat, double endLng) {
        List<PedestrianRouteDto> routes = new ArrayList<>();

        // 1. Try OpenRouteService if API key configured
        if (orsApiKey != null && !orsApiKey.isBlank()) {
            routes = queryOpenRouteService(startLat, startLng, endLat, endLng);
        }

        // 2. Fall back to OSRM walking engine if ORS not configured or produced no routes
        if (routes.isEmpty()) {
            routes = queryOsrmWalking(startLat, startLng, endLat, endLng);
        }

        // 3. Ensure at least two distinct pedestrian alternatives are generated
        if (routes.size() == 1) {
            PedestrianRouteDto primary = routes.get(0);
            PedestrianRouteDto alternative = generateScenicAlternative(startLat, startLng, endLat, endLng, primary);
            if (alternative != null) {
                routes.add(alternative);
            }
        }

        return routes;
    }

    private List<PedestrianRouteDto> queryOpenRouteService(double startLat, double startLng, double endLat, double endLng) {
        List<PedestrianRouteDto> routes = new ArrayList<>();
        try {
            String uri = String.format("%s/v2/directions/foot-walking/geojson", orsUrl);
            Map<String, Object> body = Map.of(
                    "coordinates", List.of(List.of(startLng, startLat), List.of(endLng, endLat)),
                    "alternative_routes", Map.of("target_count", 3, "weight_factor", 1.4),
                    "instructions", true,
                    "elevation", false
            );

            String rawJson = webClient.post()
                    .uri(uri)
                    .header("Authorization", orsApiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(5000))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode features = root.path("features");
            if (features.isArray()) {
                int idx = 1;
                for (JsonNode f : features) {
                    JsonNode geom = f.path("geometry").path("coordinates");
                    JsonNode summary = f.path("properties").path("summary");
                    double distance = summary.path("distance").asDouble();
                    double durationMin = summary.path("duration").asDouble() / 60.0;

                    List<List<Double>> coords = parseGeoJsonCoordinates(geom);

                    List<StepInstructionDto> steps = new ArrayList<>();
                    JsonNode segments = f.path("properties").path("segments");
                    if (segments.isArray()) {
                        for (JsonNode seg : segments) {
                            JsonNode segSteps = seg.path("steps");
                            if (segSteps.isArray()) {
                                for (JsonNode st : segSteps) {
                                    steps.add(new StepInstructionDto(
                                            st.path("instruction").asText(),
                                            st.path("distance").asDouble(),
                                            st.path("duration").asDouble(),
                                            st.path("name").asText("Walkway")
                                    ));
                                }
                            }
                        }
                    }

                    if (coords.size() >= 2) {
                        String name = idx == 1 ? "Direct pedestrian path" : (idx == 2 ? "Alternative walkway" : "Extended shaded route");
                        routes.add(new PedestrianRouteDto("ors-route-" + idx, name, coords, distance, durationMin, steps));
                        idx++;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("OpenRouteService query failed or quota exceeded: {}. Falling back to OSRM.", ex.getMessage());
        }
        return routes;
    }

    private List<PedestrianRouteDto> queryOsrmWalking(double startLat, double startLng, double endLat, double endLng) {
        List<PedestrianRouteDto> routes = new ArrayList<>();
        try {
            String uri = String.format(
                    "%s/route/v1/driving/%.5f,%.5f;%.5f,%.5f?overview=full&geometries=geojson&alternatives=true&steps=true",
                    osrmUrl, startLng, startLat, endLng, endLat
            );

            String rawJson = webClient.get()
                    .uri(uri)
                    .header("User-Agent", "MargNova-Pedestrian-Demo/1.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(12))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode routeList = root.path("routes");
            if (routeList.isArray()) {
                int idx = 1;
                for (JsonNode rNode : routeList) {
                    double distance = rNode.path("distance").asDouble();
                    double durationMin = rNode.path("duration").asDouble() / 60.0;
                    JsonNode geomCoords = rNode.path("geometry").path("coordinates");

                    List<List<Double>> coords = parseGeoJsonCoordinates(geomCoords);

                    List<StepInstructionDto> steps = new ArrayList<>();
                    JsonNode legs = rNode.path("legs");
                    if (legs.isArray()) {
                        for (JsonNode leg : legs) {
                            JsonNode legSteps = leg.path("steps");
                            if (legSteps.isArray()) {
                                for (JsonNode st : legSteps) {
                                    String instruction = st.path("maneuver").path("type").asText("walk") + " " +
                                            st.path("maneuver").path("modifier").asText("") + " on " +
                                            st.path("name").asText("footpath");
                                    steps.add(new StepInstructionDto(
                                            instruction.trim(),
                                            st.path("distance").asDouble(),
                                            st.path("duration").asDouble(),
                                            st.path("name").asText("Pedestrian path")
                                    ));
                                }
                            }
                        }
                    }

                    if (coords.size() >= 2) {
                        String name = idx == 1 ? "Direct pedestrian path" : "Alternative walking route";
                        routes.add(new PedestrianRouteDto("osrm-route-" + idx, name, coords, distance, durationMin, steps));
                        idx++;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Pedestrian routing request failed for {}: {}", osrmUrl, ex.getMessage());
        }
        return routes;
    }

    private PedestrianRouteDto generateScenicAlternative(double startLat, double startLng, double endLat, double endLng, PedestrianRouteDto primary) {
        try {
            // Generate a via-waypoint slightly offset perpendicular to direct line to query alternative pedestrian road
            double midLat = (startLat + endLat) / 2.0;
            double midLng = (startLng + endLng) / 2.0;
            double dLat = endLat - startLat;
            double dLng = endLng - startLng;
            
            // 25% perpendicular offset for realistic pedestrian variation
            double viaLat = midLat - (dLng * 0.25);
            double viaLng = midLng + (dLat * 0.25);

            String uri = String.format(
                    "%s/route/v1/driving/%.5f,%.5f;%.5f,%.5f;%.5f,%.5f?overview=full&geometries=geojson&steps=false",
                    osrmUrl, startLng, startLat, viaLng, viaLat, endLng, endLat
            );

            String rawJson = webClient.get()
                    .uri(uri)
                    .header("User-Agent", "MargNova-Pedestrian-Demo/1.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(8))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode rNode = root.path("routes").get(0);
            if (rNode != null) {
                double distance = rNode.path("distance").asDouble();
                double durationMin = rNode.path("duration").asDouble() / 60.0;
                JsonNode geomCoords = rNode.path("geometry").path("coordinates");

                List<List<Double>> coords = parseGeoJsonCoordinates(geomCoords);

                if (coords.size() >= 2) {
                    return new PedestrianRouteDto("route-alt-scenic", "Park & canopy alternative", coords, distance, durationMin, List.of());
                }
            }
        } catch (Exception ex) {
            log.debug("Scenic alternative calculation skipped: {}", ex.getMessage());
        }
        return null;
    }

    /**
     * Converts GeoJSON coordinates ([longitude, latitude]) into Leaflet-compatible [latitude, longitude].
     * Validates bounds and normalizes coordinate orientation.
     */
    private List<List<Double>> parseGeoJsonCoordinates(JsonNode geomCoords) {
        List<List<Double>> coords = new ArrayList<>();
        if (geomCoords != null && geomCoords.isArray()) {
            for (JsonNode pt : geomCoords) {
                if (pt.isArray() && pt.size() >= 2) {
                    double c0 = pt.get(0).asDouble();
                    double c1 = pt.get(1).asDouble();
                    double lat;
                    double lng;

                    // Standard GeoJSON is [lon, lat]. In world bounds: lat in [-90, 90], lon in [-180, 180].
                    if (Math.abs(c0) > 90.0 && Math.abs(c1) <= 90.0) {
                        lng = c0;
                        lat = c1;
                    } else if (Math.abs(c1) > 90.0 && Math.abs(c0) <= 90.0) {
                        lng = c1;
                        lat = c0;
                    } else {
                        // Standard GeoJSON mapping: pt[0] is longitude, pt[1] is latitude
                        lng = c0;
                        lat = c1;
                    }

                    if (lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0) {
                        coords.add(List.of(lat, lng));
                    }
                }
            }
        }
        return coords;
    }
}
