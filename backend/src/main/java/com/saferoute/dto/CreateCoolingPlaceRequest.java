
package com.saferoute.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCoolingPlaceRequest(

    @NotBlank
    @Size(max = 128)
    String name,

    @NotBlank
    @Pattern(regexp = "TEA_STALL|SHOP|PARK|WATER_POINT|BUS_STOP|TREE_SHADE|REST_AREA|INDOOR|OTHER")
    String category,

    @NotNull
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    Double latitude,

    @NotNull
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    Double longitude,

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("100.0")
    Double shadeScore,

    Boolean seatingAvailable,

    Boolean waterAvailable,

    String shadeType,

    @Size(max = 128)
    String openingHours,

    @Size(max = 255)
    String description,

    @Size(max = 100)
    String submittedBy
) {}
