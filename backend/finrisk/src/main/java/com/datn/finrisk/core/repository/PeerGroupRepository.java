package com.datn.finrisk.core.repository;

import com.datn.finrisk.core.entities.PeerGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

@org.springframework.stereotype.Repository
public interface PeerGroupRepository extends JpaRepository<PeerGroup, Long> {

    Optional<PeerGroup> findByAgeRangeAndRegion(String ageRange, String region);
}
