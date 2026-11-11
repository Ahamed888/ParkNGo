package com.example.parkngo.staff;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.reservation.ReservationRepository;
import com.example.parkngo.space.ParkingSpace;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.Staff;
import com.example.parkngo.user.StaffRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffFacilityAccessServiceTest {
    @Mock private StaffRepository staffRepository;
    @Mock private ParkingFacilityRepository facilityRepository;
    @Mock private ParkingSpaceRepository spaceRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private SecurityUtil security;
    @InjectMocks private StaffFacilityAccessService access;

    @Test
    void assignedFacilityAccessSucceeds() {
        Staff staff = staff(10L, 3L);
        when(security.getCurrentUser()).thenReturn(staff);
        when(security.hasRole("STAFF")).thenReturn(true);
        when(staffRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertEquals(3L, access.requireFacility(3L).getId());
    }

    @Test
    void alteredFacilityIdIsDenied() {
        Staff staff = staff(10L, 3L);
        when(security.getCurrentUser()).thenReturn(staff);
        when(security.hasRole("STAFF")).thenReturn(true);
        when(staffRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThrows(AccessDeniedException.class, () -> access.requireFacility(99L));
    }

    @Test
    void alteredSpaceIdOutsideAssignedFacilityIsDenied() {
        Staff staff = staff(10L, 3L);
        ParkingSpace foreignSpace = new ParkingSpace();
        ParkingFacility foreignFacility = new ParkingFacility();
        foreignFacility.setId(9L);
        foreignSpace.setFacility(foreignFacility);
        when(security.getCurrentUser()).thenReturn(staff);
        when(security.hasRole("STAFF")).thenReturn(true);
        when(staffRepository.findById(10L)).thenReturn(Optional.of(staff));
        when(spaceRepository.findById(77L)).thenReturn(Optional.of(foreignSpace));

        assertThrows(AccessDeniedException.class, () -> access.requireSpace(77L));
    }

    @Test
    void unassignedStaffIsDenied() {
        Staff staff = staff(10L, null);
        when(security.getCurrentUser()).thenReturn(staff);
        when(security.hasRole("STAFF")).thenReturn(true);
        when(staffRepository.findById(10L)).thenReturn(Optional.of(staff));

        assertThrows(AccessDeniedException.class, access::currentAssignedFacility);
    }

    private Staff staff(Long id, Long facilityId) {
        Staff staff = new Staff();
        staff.setId(id);
        if (facilityId != null) {
            ParkingFacility facility = new ParkingFacility();
            facility.setId(facilityId);
            staff.setAssignedFacility(facility);
        }
        return staff;
    }
}
