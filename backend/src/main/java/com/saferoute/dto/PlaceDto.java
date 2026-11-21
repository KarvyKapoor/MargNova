
package com.saferoute.dto;

public record PlaceDto(
    String id,
    String name,
    String category,
    Double latitude,
    Double longitude,
    Double shadeScore,
    Boolean isVerified,
    String openingHours,
    String description,
    DataTrustLevel dataTrust,
    Double distanceMeters,
    Boolean seatingAvailable,
    Boolean waterAvailable,
    String shadeType,
    String submittedBy,
    String verificationStatus
) {}
