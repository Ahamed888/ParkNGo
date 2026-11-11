package com.example.parkngo.staff;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.reservation.Reservation;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.Staff;
import com.example.parkngo.user.StaffRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-side guard for every staff operation that addresses facility data. */
@Service
@Transactional(readOnly = true)
public class StaffFacilityAccessService {
    private final StaffRepository staffRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final ParkingSpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final SecurityUtil security;

    public StaffFacilityAccessService(StaffRepository staffRepository,
                                      ParkingFacilityRepository facilityRepository,
                                      ParkingSpaceRepository spaceRepository,
                                      ReservationRepository reservationRepository,
                                      SecurityUtil security) {
        this.staffRepository = staffRepository;
        this.facilityRepository = facilityRepository;
        this.spaceRepository = spaceRepository;
        this.reservationRepository = reservationRepository;
        this.security = security;
    }

    public ParkingFacility currentAssignedFacility() {
        User user = security.getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("A staff account is required.");
        }
        if (security.hasRole("STAFF")) {
            Staff staff = staffRepository.findById(user.getId()).orElse(null);
            if (staff != null && staff.getAssignedFacility() != null) {
                return staff.getAssignedFacility();
            }
        }
        return facilityRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new AccessDeniedException("No parking facility is configured in the system."));
    }

    public ParkingFacility requireFacility(Long facilityId) {
        if (facilityId != null) {
            return facilityRepository.findById(facilityId)
                    .orElseGet(this::currentAssignedFacility);
        }
        return currentAssignedFacility();
    }

    public ParkingSpace requireSpace(Long spaceId) {
        return spaceRepository.findById(spaceId)
                .orElseThrow(() -> new IllegalArgumentException("Parking space not found."));
    }

    public Reservation requireReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found."));
        ParkingFacility assigned = currentAssignedFacility();
        if (reservation.getParkingSpace() == null || reservation.getParkingSpace().getFacility() == null
                || !assigned.getId().equals(reservation.getParkingSpace().getFacility().getId())) {
            throw new AccessDeniedException("This reservation is outside your assigned facility.");
        }
        return reservation;
    }

    @Transactional
    public Staff assign(Long staffId, Long facilityId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found."));
        ParkingFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Parking facility not found."));
        staff.setAssignedFacility(facility);
        return staffRepository.save(staff);
    }

    @Transactional
    public Staff clearAssignment(Long staffId) {
        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new IllegalArgumentException("Staff account not found."));
        staff.setAssignedFacility(null);
        return staffRepository.save(staff);
    }
}
