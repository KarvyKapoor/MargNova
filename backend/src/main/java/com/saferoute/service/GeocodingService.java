package com.saferoute.service;

import com.saferoute.dto.SearchResultDto;
import com.saferoute.integration.GeocodingClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeocodingService {

    private final GeocodingClient geocodingClient;

    public GeocodingService(GeocodingClient geocodingClient) {
        this.geocodingClient = geocodingClient;
    }

    public List<SearchResultDto> search(String query, Double lat, Double lng) {
        return geocodingClient.searchPlaces(query, lat, lng);
    }

    public SearchResultDto reverse(Double lat, Double lng) {
        return geocodingClient.reverseGeocode(lat, lng);
    }
}

