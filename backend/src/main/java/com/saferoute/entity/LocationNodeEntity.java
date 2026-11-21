package com.saferoute.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "location_nodes")
public class LocationNodeEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(nullable = false)
    private Double shade;

    @Column(name = "cooling_name", length = 128)
    private String coolingName;

    @Column(name = "data_trust", nullable = false, length = 32)
    private String dataTrust = "SIMULATED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public LocationNodeEntity() {}

    public LocationNodeEntity(String id, String name, Double lat, Double lng, Double shade, String coolingName, String dataTrust) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.shade = shade;
        this.coolingName = coolingName;
        this.dataTrust = dataTrust != null ? dataTrust : "SIMULATED";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getShade() { return shade; }
    public void setShade(Double shade) { this.shade = shade; }

    public String getCoolingName() { return coolingName; }
    public void setCoolingName(String coolingName) { this.coolingName = coolingName; }

    public String getDataTrust() { return dataTrust; }
    public void setDataTrust(String dataTrust) { this.dataTrust = dataTrust; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}

