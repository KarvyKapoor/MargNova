
package com.saferoute.service;

import com.saferoute.dto.*;
import com.saferoute.entity.CoolingPlaceEntity;
import com.saferoute.repository.CoolingPlaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PlaceService {

    private final CoolingPlaceRepository repository;

    public PlaceService(CoolingPlaceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PlaceDto addCoolingPlace(CreateCoolingPlaceRequest request) {

        // Prevent obvious duplicate submissions within 20 metres.
        List<CoolingPlaceEntity> nearby =
            repository.findNearby(
                request.latitude(),
                request.longitude(),
                20
            );

        boolean duplicate = nearby.stream().anyMatch(p ->
            p.getName().equalsIgnoreCase(request.name().trim())
            && p.getCategory().equalsIgnoreCase(request.category())
        );

        if (duplicate) {
            throw new IllegalArgumentException(
                "A similar cooling point already exists nearby."
            );
        }

        CoolingPlaceEntity place = new CoolingPlaceEntity();

        place.setId(UUID.randomUUID().toString());
        place.setName(request.name().trim());
        place.setCategory(request.category());
        place.setLat(request.latitude());
        place.setLng(request.longitude());
        place.setShadeScore(request.shadeScore());
        place.setSeatingAvailable(
            Boolean.TRUE.equals(request.seatingAvailable())
        );
        place.setWaterAvailable(
            Boolean.TRUE.equals(request.waterAvailable())
        );
        place.setShadeType(request.shadeType());
        place.setOpeningHours(request.openingHours());
        place.setDescription(request.description());
        place.setSubmittedBy(request.submittedBy());

        // Community submissions are not automatically trusted.
        place.setIsVerified(false);
        place.setVerificationStatus("PENDING");
        place.setDataTrust("UNKNOWN");

        return toDto(repository.save(place),
            request.latitude(), request.longitude());
    }

    @Transactional(readOnly = true)
    public List<PlaceDto> getNearbyPlaces(
        Double lat, Double lng, Double radiusM
    ) {
        double queryLat = lat != null ? lat : 23.2610;
        double queryLng = lng != null ? lng : 77.4120;
        double queryRadius = radiusM != null ? radiusM : 1500.0;

        List<CoolingPlaceEntity> places =
            repository.findNearby(
                queryLat, queryLng, queryRadius
            );

        // Include both pending community suggestions and verified places
        // so users can see community contributions with their trust status.
        return places.stream()
            .map(p -> toDto(p, queryLat, queryLng))
            .collect(Collectors.toList());
    }

    private PlaceDto toDto(
        CoolingPlaceEntity entity,
        double lat,
        double lng
    ) {
        double distance = calculateDistanceMeters(
            lat, lng, entity.getLat(), entity.getLng()
        );

        DataTrustLevel trust;

        try {
            trust = DataTrustLevel.valueOf(entity.getDataTrust());
        } catch (Exception ex) {
            trust = DataTrustLevel.UNKNOWN;
        }

        return new PlaceDto(
            entity.getId(),
            entity.getName(),
            entity.getCategory(),
            entity.getLat(),
            entity.getLng(),
            entity.getShadeScore(),
            entity.getIsVerified(),
            entity.getOpeningHours(),
            entity.getDescription(),
            trust,
            Math.round(distance * 10.0) / 10.0,
            entity.getSeatingAvailable(),
            entity.getWaterAvailable(),
            entity.getShadeType(),
            entity.getSubmittedBy(),
            entity.getVerificationStatus()
        );
    }

    private double calculateDistanceMeters(
        double lat1, double lng1,
        double lat2, double lng2
    ) {
        double latDistance =
            Math.toRadians(lat2 - lat1);

        double lngDistance =
            Math.toRadians(lng2 - lng1);

        double a =
            Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1))
            * Math.cos(Math.toRadians(lat2))
            * Math.sin(lngDistance / 2)
            * Math.sin(lngDistance / 2);

        return 6371000 * 2 * Math.atan2(
            Math.sqrt(a), Math.sqrt(1 - a)
        );
    }
}