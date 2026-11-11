package com.example.parkngo.config;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.mockito.Mockito.*;

class DataInitializerStaffAssignmentTest {
    private StaffRepository staffRepository;
    private ParkingFacilityRepository facilityRepository;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        staffRepository = mock(StaffRepository.class);
        facilityRepository = mock(ParkingFacilityRepository.class);
        initializer = new DataInitializer(mock(RoleRepository.class), staffRepository,
                mock(CustomerRepository.class), mock(AdminRepository.class), facilityRepository,
                mock(ParkingSpaceRepository.class), mock(BCryptPasswordEncoder.class), mock(JdbcTemplate.class));
    }

    @Test
    void autoAssignsWhenExactlyOneActiveFacilityExists() {
        ParkingFacility facility = facility(1L, ParkingFacility.FacilityStatus.ACTIVE);
        Staff staff = new Staff();
        when(facilityRepository.findAll()).thenReturn(List.of(facility));
        when(staffRepository.findAll()).thenReturn(List.of(staff));

        initializer.assignUnassignedStaffWhenThereIsOneFacility();

        verify(staffRepository).save(staff);
        org.junit.jupiter.api.Assertions.assertSame(facility, staff.getAssignedFacility());
    }

    @Test
    void doesNothingWhenThereAreNoActiveFacilities() {
        when(facilityRepository.findAll()).thenReturn(List.of(
                facility(1L, ParkingFacility.FacilityStatus.INACTIVE)));

        initializer.assignUnassignedStaffWhenThereIsOneFacility();

        verify(staffRepository, never()).findAll();
        verify(staffRepository, never()).save(any());
    }

    @Test
    void doesNotAutoAssignWhenMultipleActiveFacilitiesExist() {
        when(facilityRepository.findAll()).thenReturn(List.of(
                facility(1L, ParkingFacility.FacilityStatus.ACTIVE),
                facility(2L, ParkingFacility.FacilityStatus.ACTIVE)));

        initializer.assignUnassignedStaffWhenThereIsOneFacility();

        verify(staffRepository, never()).findAll();
        verify(staffRepository, never()).save(any());
    }

    private ParkingFacility facility(Long id, ParkingFacility.FacilityStatus status) {
        ParkingFacility facility = new ParkingFacility();
        facility.setId(id);
        facility.setStatus(status);
        return facility;
    }
}
