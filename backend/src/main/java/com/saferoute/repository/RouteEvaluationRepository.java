package com.saferoute.repository;

import com.saferoute.entity.RouteEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RouteEvaluationRepository extends JpaRepository<RouteEvaluationEntity, String> {
    List<RouteEvaluationEntity> findTop10ByOrderByCreatedAtDesc();
}

