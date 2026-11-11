package com.example.parkngo.vehicle;

import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class VehicleService {
    private final VehicleRepository vehicleRepository;
    private final ReservationRepository reservationRepository;

    public VehicleService(VehicleRepository vehicleRepository, ReservationRepository reservationRepository) {
        this.vehicleRepository = vehicleRepository;
        this.reservationRepository = reservationRepository;
    }

    public Vehicle registerVehicle(Vehicle vehicle) throws Exception {
        if (vehicle.getRegistrationNumber() == null || vehicle.getRegistrationNumber().trim().isEmpty()) {
            throw new IllegalArgumentException("License plate number is required");
        }

        String normalizedPlate = vehicle.getRegistrationNumber().trim().toUpperCase();
        vehicle.setRegistrationNumber(normalizedPlate);

        if (vehicleRepository.existsByRegistrationNumber(normalizedPlate)) {
            throw new IllegalArgumentException("Vehicle with registration number '" + normalizedPlate + "' is already registered");
        }

        if (vehicle.getStatus() == null) {
            vehicle.setStatus(Vehicle.VehicleStatus.ACTIVE);
        }
        if (vehicle.getCreatedAt() == null) {
            vehicle.setCreatedAt(LocalDateTime.now());
        }

        return vehicleRepository.save(vehicle);
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicleById(Long id) throws Exception {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new Exception("Vehicle not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public Vehicle getVehicleByIdAndCustomer(Long id, Long customerId) throws Exception {
        return vehicleRepository.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new Exception("Vehicle not found or you do not have permission to view it"));
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getCustomerVehicles(Long customerId) {
        return vehicleRepository.findByCustomerIdAndStatus(customerId, Vehicle.VehicleStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Vehicle> getActiveCustomerVehicles(Long customerId) {
        return vehicleRepository.findByCustomerIdAndStatus(customerId, Vehicle.VehicleStatus.ACTIVE);
    }

    public Vehicle updateVehicle(Vehicle vehicle, Long customerId) throws Exception {
        Vehicle existing = getVehicleByIdAndCustomer(vehicle.getId(), customerId);

        String normalizedPlate = vehicle.getRegistrationNumber() != null ? vehicle.getRegistrationNumber().trim().toUpperCase() : existing.getRegistrationNumber();

        if (vehicleRepository.existsByRegistrationNumberAndIdNot(normalizedPlate, existing.getId())) {
            throw new IllegalArgumentException("Another vehicle with registration number '" + normalizedPlate + "' already exists");
        }

        existing.setRegistrationNumber(normalizedPlate);
        existing.setVehicleType(vehicle.getVehicleType());
        existing.setBrand(vehicle.getBrand());
        existing.setModel(vehicle.getModel());
        existing.setColor(vehicle.getColor());
        if (vehicle.getStatus() != null) {
            existing.setStatus(vehicle.getStatus());
        }

        return vehicleRepository.save(existing);
    }

    public Vehicle updateVehicle(Vehicle vehicle) throws Exception {
        Vehicle existing = getVehicleById(vehicle.getId());
        if (vehicle.getRegistrationNumber() != null) {
            String normalizedPlate = vehicle.getRegistrationNumber().trim().toUpperCase();
            if (vehicleRepository.existsByRegistrationNumberAndIdNot(normalizedPlate, existing.getId())) {
                throw new IllegalArgumentException("Another vehicle with registration number '" + normalizedPlate + "' already exists");
            }
            existing.setRegistrationNumber(normalizedPlate);
        }
        existing.setVehicleType(vehicle.getVehicleType());
        existing.setColor(vehicle.getColor());
        existing.setBrand(vehicle.getBrand());
        existing.setModel(vehicle.getModel());
        if (vehicle.getStatus() != null) {
            existing.setStatus(vehicle.getStatus());
        }
        return vehicleRepository.save(existing);
    }

    public void deleteVehicle(Long id, Long customerId) throws Exception {
        Vehicle existing = getVehicleByIdAndCustomer(id, customerId);
        ensureVehicleHasNoLiveReservations(existing.getId());

        // Check if vehicle has any reservation records (past, active or upcoming)
        long totalReservations = reservationRepository.countByVehicleId(existing.getId());
        if (totalReservations > 0) {
            // Soft delete: Mark vehicle as INACTIVE to remove from customer's list while preserving reservation data
            existing.setStatus(Vehicle.VehicleStatus.INACTIVE);
            vehicleRepository.save(existing);
        } else {
            // No reservation records exist - safe to delete permanently
            try {
                vehicleRepository.delete(existing);
            } catch (Exception e) {
                existing.setStatus(Vehicle.VehicleStatus.INACTIVE);
                vehicleRepository.save(existing);
            }
        }
    }

    public void deleteVehicle(Long id) throws Exception {
        Vehicle existing = getVehicleById(id);
        ensureVehicleHasNoLiveReservations(existing.getId());
        long totalReservations = reservationRepository.countByVehicleId(existing.getId());
        if (totalReservations > 0) {
            existing.setStatus(Vehicle.VehicleStatus.INACTIVE);
            vehicleRepository.save(existing);
        } else {
            try {
                vehicleRepository.delete(existing);
            } catch (Exception e) {
                existing.setStatus(Vehicle.VehicleStatus.INACTIVE);
                vehicleRepository.save(existing);
            }
        }
    }

    private void ensureVehicleHasNoLiveReservations(Long vehicleId) {
        List<Reservation> liveReservations = reservationRepository.findActiveOrUpcomingReservationsByVehicleId(vehicleId);
        if (!liveReservations.isEmpty()) {
            throw new IllegalStateException(
                    "This vehicle cannot be removed while it has a pending, confirmed, or active reservation. " +
                    "Cancel or complete the reservation first.");
        }
    }

    @Transactional(readOnly = true)
    public boolean isVehicleRegistered(String registrationNumber) {
        if (registrationNumber == null) return false;
        return vehicleRepository.existsByRegistrationNumber(registrationNumber.trim().toUpperCase());
    }
}
