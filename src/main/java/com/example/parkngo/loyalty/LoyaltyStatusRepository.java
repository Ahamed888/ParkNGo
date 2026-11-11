package com.example.parkngo.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoyaltyStatusRepository extends JpaRepository<LoyaltyStatus, Long> {
}
