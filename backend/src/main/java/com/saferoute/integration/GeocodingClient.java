package com.saferoute.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saferoute.dto.SearchResultDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GeocodingClient {

    private static final Logger log = LoggerFactory.getLogger(GeocodingClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String photonUrl;
    private final String nominatimUrl;

    private final Map<String, List<SearchResultDto>> searchCache = new ConcurrentHashMap<>();

    public GeocodingClient(
            WebClient webClient,
            ObjectMapper objectMapper,
            @Value("${saferoute.photon.url:https://photon.komoot.io/api}") String photonUrl,
            @Value("${saferoute.nominatim.url:https://nominatim.openstreetmap.org}") String nominatimUrl) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.photonUrl = photonUrl;
        this.nominatimUrl = nominatimUrl;
    }

    public List<SearchResultDto> searchPlaces(String query, Double lat, Double lng) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String cleanQuery = query.trim().toLowerCase();
        if (searchCache.containsKey(cleanQuery)) {
            return searchCache.get(cleanQuery);
        }

        List<SearchResultDto> results = searchViaPhoton(cleanQuery, lat, lng);
        if (results.isEmpty()) {
            results = searchViaNominatim(cleanQuery);
        }

        if (!results.isEmpty()) {
            searchCache.put(cleanQuery, results);
        }
        return results;
    }

    public SearchResultDto reverseGeocode(double lat, double lng) {
        try {
            String uri = String.format("https://photon.komoot.io/reverse?lat=%.5f&lon=%.5f", lat, lng);
            String rawJson = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(3500))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode features = root.path("features");
            if (features.isArray() && !features.isEmpty()) {
                JsonNode prop = features.get(0).path("properties");
                String name = prop.path("name").asText("Current Location");
                String street = prop.path("street").asText("");
                String city = prop.path("city").asText(prop.path("district").asText(""));
                String country = prop.path("country").asText("");
                String displayName = buildDisplayName(name, street, city, country);

                return new SearchResultDto(name, displayName, lat, lng, city, prop.path("state").asText(""), country, "reverse_geocode");
            }
        } catch (Exception ex) {
            log.warn("Reverse geocode via Photon failed: {}", ex.getMessage());
        }

        // Fallback representation
        return new SearchResultDto("Selected Location", String.format("Coordinates: %.4f, %.4f", lat, lng), lat, lng, "", "", "", "coordinates");
    }

    private List<SearchResultDto> searchViaPhoton(String query, Double lat, Double lng) {
        List<SearchResultDto> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String uri = String.format("%s/?q=%s&limit=6", photonUrl, encoded);
            if (lat != null && lng != null) {
                uri += String.format("&lat=%.4f&lon=%.4f", lat, lng);
            }

            String rawJson = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(3500))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode features = root.path("features");
            if (features.isArray()) {
                for (JsonNode f : features) {
                    JsonNode coords = f.path("geometry").path("coordinates");
                    JsonNode prop = f.path("properties");
                    if (coords.isArray() && coords.size() >= 2) {
                        double itemLng = coords.get(0).asDouble();
                        double itemLat = coords.get(1).asDouble();
                        String name = prop.path("name").asText(prop.path("street").asText("Location"));
                        String street = prop.path("street").asText("");
                        String city = prop.path("city").asText(prop.path("district").asText(""));
                        String state = prop.path("state").asText("");
                        String country = prop.path("country").asText("");
                        String type = prop.path("osm_value").asText(prop.path("type").asText("place"));
                        String displayName = buildDisplayName(name, street, city, country);

                        results.add(new SearchResultDto(name, displayName, itemLat, itemLng, city, state, country, type));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Photon search failed for query '{}': {}", query, ex.getMessage());
        }
        return results;
    }

    private List<SearchResultDto> searchViaNominatim(String query) {
        List<SearchResultDto> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String uri = String.format("%s/search?format=json&q=%s&limit=5", nominatimUrl, encoded);

            String rawJson = webClient.get()
                    .uri(uri)
                    .header("User-Agent", "SafeRoute-MargNova-App/1.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(4000))
                    .block();

            JsonNode root = objectMapper.readTree(rawJson);
            if (root.isArray()) {
                for (JsonNode node : root) {
                    double lat = node.path("lat").asDouble();
                    double lon = node.path("lon").asDouble();
                    String displayName = node.path("display_name").asText();
                    String name = node.path("name").asText(displayName.split(",")[0]);
                    String type = node.path("type").asText("place");

                    results.add(new SearchResultDto(name, displayName, lat, lon, "", "", "", type));
                }
            }
        } catch (Exception ex) {
            log.warn("Nominatim fallback search failed for query '{}': {}", query, ex.getMessage());
        }
        return results;
    }

    private String buildDisplayName(String name, String street, String city, String country) {
        List<String> parts = new ArrayList<>();
        if (name != null && !name.isBlank()) parts.add(name);
        if (street != null && !street.isBlank() && !street.equalsIgnoreCase(name)) parts.add(street);
        if (city != null && !city.isBlank() && !city.equalsIgnoreCase(name)) parts.add(city);
        if (country != null && !country.isBlank()) parts.add(country);
        return String.join(", ", parts);
    }
}

