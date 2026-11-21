package com.saferoute.service;

import com.saferoute.dto.WeatherResponseDto;
import com.saferoute.integration.WeatherClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class WeatherService {

    private final WeatherClient weatherClient;

    public WeatherService(WeatherClient weatherClient) {
        this.weatherClient = weatherClient;
    }

    public WeatherResponseDto getWeather(Double lat, Double lng) {
        double targetLat = lat != null ? lat : 23.2599;
        double targetLng = lng != null ? lng : 77.4126;
        return weatherClient.getWeather(targetLat, targetLng);
    }

    public Map<String, Object> checkStatus() {
        return weatherClient.checkStatus();
    }
}

