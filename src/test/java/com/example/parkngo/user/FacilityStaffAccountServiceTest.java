package com.example.parkngo.user;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FacilityStaffAccountServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private StaffRepository staffRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ParkingFacilityRepository facilityRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;

    private FacilityStaffAccountService service;
    private Role staffRole;
    private ParkingFacility facility;

    @BeforeEach
    void setUp() {
        service = new FacilityStaffAccountService(userRepository, staffRepository, roleRepository,
                facilityRepository, passwordEncoder);
        staffRole = new Role(2L, "STAFF", "Parking facility staff");
        facility = new ParkingFacility();
        facility.setId(4L);
        facility.setName("Nugegoda High-Street Parking");
        facility.setStatus(ParkingFacility.FacilityStatus.ACTIVE);
    }

    @Test
    void createsDedicatedStaffAccountAssignedToFacility() {
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(userRepository.findByEmail("facility4@staff.parkngo.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("ParkNGo@Staff4")).thenReturn("encoded-password");

        FacilityStaffAccountService.StaffCredentials credentials = service.ensureStaffAccount(facility);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(captor.capture());
        Staff saved = captor.getValue();
        assertSame(facility, saved.getAssignedFacility());
        assertSame(staffRole, saved.getRole());
        assertEquals(User.UserStatus.ACTIVE, saved.getStatus());
        assertEquals("FAC-4", saved.getEmployeeId());
        assertEquals("encoded-password", saved.getPassword());
        assertEquals("facility4@staff.parkngo.local", credentials.email());
        assertEquals("ParkNGo@Staff4", credentials.password());
    }

    @Test
    void reusesGeneratedStaffAccountWithoutCreatingDuplicate() {
        Staff existing = new Staff();
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(userRepository.findByEmail("facility4@staff.parkngo.local")).thenReturn(Optional.of(existing));

        service.ensureStaffAccount(facility);

        verify(staffRepository).save(existing);
        verify(passwordEncoder, never()).encode(anyString());
        assertSame(facility, existing.getAssignedFacility());
        assertEquals("facility4@staff.parkngo.local", existing.getEmail());
    }

    @Test
    void inactiveFacilityProducesInactiveStaffAccount() {
        facility.setStatus(ParkingFacility.FacilityStatus.INACTIVE);
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(staffRole));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        service.ensureStaffAccount(facility);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(captor.capture());
        assertEquals(User.UserStatus.INACTIVE, captor.getValue().getStatus());
    }
}
