package com.saferoute.service;

import com.saferoute.controller.RouteController.RoutePayload;
import com.saferoute.dto.RouteRequestDto;
import com.saferoute.integration.RouteIntelligenceClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class IntelligenceService {

    private final RouteIntelligenceClient client;
    private final RouteService routeService;

    public IntelligenceService(RouteIntelligenceClient client, @Lazy RouteService routeService) {
        this.client = client;
        this.routeService = routeService;
    }

    public Object health() {
        return client.checkHealth();
    }

    public Object locations() {
        return client.getLocations();
    }

    public Object routes(RoutePayload payload) {
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
}
