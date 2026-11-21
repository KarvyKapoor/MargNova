
package com.saferoute.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "cooling_places")
public class CoolingPlaceEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(name = "shade_score", nullable = false)
    private Double shadeScore = 0.0;

    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;

    @Column(name = "seating_available", nullable = false)
    private Boolean seatingAvailable = false;

    @Column(name = "water_available", nullable = false)
    private Boolean waterAvailable = false;

    @Column(name = "shade_type", length = 40)
    private String shadeType;

    @Column(name = "opening_hours", length = 128)
    private String openingHours;

    @Column(length = 255)
    private String description;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "verification_status",
            nullable = false, length = 20)
    private String verificationStatus = "PENDING";

    @Column(name = "data_trust",
            nullable = false, length = 32)
    private String dataTrust = "UNKNOWN";

    @Column(name = "created_at",
            insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CoolingPlaceEntity() {}

    @PrePersist
    public void onCreate() {
        if (id == null) id = java.util.UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }

    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }

    public Double getShadeScore() { return shadeScore; }
    public void setShadeScore(Double shadeScore) {
        this.shadeScore = shadeScore;
    }

    public Boolean getIsVerified() { return isVerified; }
    public void setIsVerified(Boolean isVerified) {
        this.isVerified = isVerified;
    }

    public Boolean getSeatingAvailable() { return seatingAvailable; }
    public void setSeatingAvailable(Boolean seatingAvailable) {
        this.seatingAvailable = seatingAvailable;
    }

    public Boolean getWaterAvailable() { return waterAvailable; }
    public void setWaterAvailable(Boolean waterAvailable) {
        this.waterAvailable = waterAvailable;
    }

    public String getShadeType() { return shadeType; }
    public void setShadeType(String shadeType) {
        this.shadeType = shadeType;
    }

    public String getOpeningHours() { return openingHours; }
    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getDataTrust() { return dataTrust; }
    public void setDataTrust(String dataTrust) {
        this.dataTrust = dataTrust;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
