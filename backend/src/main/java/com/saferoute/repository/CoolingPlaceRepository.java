
package com.saferoute.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.saferoute.entity.CoolingPlaceEntity;

@Repository
public interface CoolingPlaceRepository
        extends JpaRepository<CoolingPlaceEntity, String> {

    List<CoolingPlaceEntity> findAllByCategory(String category);

    List<CoolingPlaceEntity> findAllByVerificationStatus(String status);

    @Query("""
        SELECT p FROM CoolingPlaceEntity p
        WHERE
        (6371000 * acos(
            least(1.0, greatest(-1.0,
                cos(radians(:lat))
                * cos(radians(p.lat))
                * cos(radians(p.lng) - radians(:lng))
                + sin(radians(:lat))
                * sin(radians(p.lat))
            ))
        )) <= :radiusM
    """)
    List<CoolingPlaceEntity> findNearby(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("radiusM") double radiusM
    );
}