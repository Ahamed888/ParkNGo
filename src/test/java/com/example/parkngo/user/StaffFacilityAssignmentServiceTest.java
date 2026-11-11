package com.example.parkngo.user;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffFacilityAssignmentServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ParkingFacilityRepository facilityRepository;
    @Mock private BCryptPasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleRepository, facilityRepository, passwordEncoder);
    }

    @Test
    void createsStaffWithActiveFacilityAssignment() throws Exception {
        ParkingFacility facility = facility(10L, ParkingFacility.FacilityStatus.ACTIVE);
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(role("STAFF")));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User saved = userService.registerUser(validUser(), "STAFF", 10L);

        assertInstanceOf(Staff.class, saved);
        assertSame(facility, ((Staff) saved).getAssignedFacility());
    }

    @Test
    void editsStaffAndChangesFacility() throws Exception {
        ParkingFacility oldFacility = facility(10L, ParkingFacility.FacilityStatus.ACTIVE);
        ParkingFacility newFacility = facility(20L, ParkingFacility.FacilityStatus.ACTIVE);
        Staff staff = validStaff(7L, oldFacility);
        when(userRepository.findById(7L)).thenReturn(Optional.of(staff));
        when(facilityRepository.findById(20L)).thenReturn(Optional.of(newFacility));
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(role("STAFF")));
        when(userRepository.save(staff)).thenReturn(staff);

        User saved = userService.adminUpdateUser(7L, staff.getFullName(), staff.getEmail(), staff.getPhone(),
                "STAFF", User.UserStatus.ACTIVE, "DAY", "", 20L);

        assertSame(newFacility, ((Staff) saved).getAssignedFacility());
    }

    @Test
    void rejectsInvalidFacilityId() {
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(role("STAFF")));
        when(facilityRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(validUser(), "STAFF", 999L));

        assertTrue(error.getMessage().contains("does not exist"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInactiveFacilityForNewAssignment() {
        when(roleRepository.findByName("STAFF")).thenReturn(Optional.of(role("STAFF")));
        when(facilityRepository.findById(10L))
                .thenReturn(Optional.of(facility(10L, ParkingFacility.FacilityStatus.INACTIVE)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(validUser(), "STAFF", 10L));

        assertTrue(error.getMessage().contains("inactive"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void customerAndAdminDoNotRequireFacilityAssignment() throws Exception {
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(role("CUSTOMER")));
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(role("ADMIN")));
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertInstanceOf(Customer.class, userService.registerUser(validUser(), "CUSTOMER", null));
        assertInstanceOf(Admin.class, userService.registerUser(validUser("admin2@example.com"), "ADMIN", null));
        verify(facilityRepository, never()).findById(anyLong());
    }

    @Test
    void failedFacilityValidationPreservesExistingStaffAssignment() {
        ParkingFacility existingFacility = facility(10L, ParkingFacility.FacilityStatus.ACTIVE);
        Staff staff = validStaff(7L, existingFacility);
        when(userRepository.findById(7L)).thenReturn(Optional.of(staff));
        when(facilityRepository.findById(20L))
                .thenReturn(Optional.of(facility(20L, ParkingFacility.FacilityStatus.INACTIVE)));

        assertThrows(IllegalArgumentException.class, () -> userService.adminUpdateUser(7L,
                staff.getFullName(), staff.getEmail(), staff.getPhone(), "STAFF",
                User.UserStatus.ACTIVE, "DAY", "", 20L));

        assertSame(existingFacility, staff.getAssignedFacility());
        verify(userRepository, never()).save(any());
    }

    private User validUser() {
        return validUser("staff2@example.com");
    }

    private User validUser(String email) {
        User user = new User();
        user.setFullName("Nimal Perera");
        user.setEmail(email);
        user.setPhone("0771234567");
        user.setPassword("ParkNGo!2026");
        return user;
    }

    private Staff validStaff(Long id, ParkingFacility facility) {
        Staff staff = new Staff();
        staff.setId(id);
        staff.setFullName("Nimal Perera");
        staff.setEmail("staff2@example.com");
        staff.setPhone("0771234567");
        staff.setPassword("encoded");
        staff.setAssignedFacility(facility);
        return staff;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private ParkingFacility facility(Long id, ParkingFacility.FacilityStatus status) {
        ParkingFacility facility = new ParkingFacility();
        facility.setId(id);
        facility.setName("Facility " + id);
        facility.setStatus(status);
        return facility;
    }
}
