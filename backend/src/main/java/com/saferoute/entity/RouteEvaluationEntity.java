package com.saferoute.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_evaluations")
public class RouteEvaluationEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 64)
    private String origin;

    @Column(nullable = false, length = 64)
    private String destination;

    @Column(nullable = false, length = 32)
    private String profile;

    @Column(name = "temperature_c", nullable = false)
    private Double temperatureC;

    @Column(name = "recommended_route_id", length = 64)
    private String recommendedRouteId;

    @Column(name = "suitability_score")
    private Double suitabilityScore;

    @Column(name = "heat_score")
    private Double heatScore;

    @Column(name = "safety_score")
    private Double safetyScore;

    @Column(name = "duration_min")
    private Double durationMin;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public RouteEvaluationEntity() {}

    public RouteEvaluationEntity(String id, String origin, String destination, String profile,
                                 Double temperatureC, String recommendedRouteId,
                                 Double suitabilityScore, Double heatScore, Double safetyScore,
                                 Double durationMin, Integer distanceM) {
        this.id = id;
        this.origin = origin;
        this.destination = destination;
        this.profile = profile;
        this.temperatureC = temperatureC;
        this.recommendedRouteId = recommendedRouteId;
        this.suitabilityScore = suitabilityScore;
        this.heatScore = heatScore;
        this.safetyScore = safetyScore;
        this.durationMin = durationMin;
        this.distanceM = distanceM;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getProfile() { return profile; }
    public void setProfile(String profile) { this.profile = profile; }

    public Double getTemperatureC() { return temperatureC; }
    public void setTemperatureC(Double temperatureC) { this.temperatureC = temperatureC; }

    public String getRecommendedRouteId() { return recommendedRouteId; }
    public void setRecommendedRouteId(String recommendedRouteId) { this.recommendedRouteId = recommendedRouteId; }

    public Double getSuitabilityScore() { return suitabilityScore; }
    public void setSuitabilityScore(Double suitabilityScore) { this.suitabilityScore = suitabilityScore; }

    public Double getHeatScore() { return heatScore; }
    public void setHeatScore(Double heatScore) { this.heatScore = heatScore; }

    public Double getSafetyScore() { return safetyScore; }
    public void setSafetyScore(Double safetyScore) { this.safetyScore = safetyScore; }

    public Double getDurationMin() { return durationMin; }
    public void setDurationMin(Double durationMin) { this.durationMin = durationMin; }

    public Integer getDistanceM() { return distanceM; }
    public void setDistanceM(Integer distanceM) { this.distanceM = distanceM; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

