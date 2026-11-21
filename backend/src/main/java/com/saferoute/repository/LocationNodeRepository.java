package com.saferoute.repository;

import com.saferoute.entity.LocationNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationNodeRepository extends JpaRepository<LocationNodeEntity, String> {
    List<LocationNodeEntity> findAllByOrderByNameAsc();
}

