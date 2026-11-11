package com.example.parkngo.vehicle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCustomerId(Long customerId);
    List<Vehicle> findByCustomerIdAndStatus(Long customerId, Vehicle.VehicleStatus status);
    Optional<Vehicle> findByIdAndCustomerId(Long id, Long customerId);
    Optional<Vehicle> findByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumber(String registrationNumber);
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long id);
}
