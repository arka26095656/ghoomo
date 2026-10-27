package com.ghoomo.itinerary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItineraryRepository extends JpaRepository<Itinerary, Long> {
    List<Itinerary> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<Itinerary> findByIdAndUserId(Long id, Long userId);
}
