package com.example.parkngo.user;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creates the dedicated staff login used to operate each parking facility. */
@Service
@Transactional
public class FacilityStaffAccountService {
    private static final String STAFF_EMAIL_DOMAIN = "@staff.parkngo.local";
    private static final String STAFF_PASSWORD_PREFIX = "ParkNGo@Staff";

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final RoleRepository roleRepository;
    private final ParkingFacilityRepository facilityRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public FacilityStaffAccountService(UserRepository userRepository,
                                       StaffRepository staffRepository,
                                       RoleRepository roleRepository,
                                       ParkingFacilityRepository facilityRepository,
                                       BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.roleRepository = roleRepository;
        this.facilityRepository = facilityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public StaffCredentials ensureStaffAccount(ParkingFacility facility) {
        if (facility == null || facility.getId() == null) {
            throw new IllegalArgumentException("A saved parking facility is required before creating its staff account.");
        }

        Role staffRole = roleRepository.findByName("STAFF")
                .orElseThrow(() -> new IllegalStateException("The STAFF role is not configured."));
        String email = emailFor(facility.getId());
        String rawPassword = passwordFor(facility.getId());

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null && !(existing instanceof Staff)) {
            throw new IllegalStateException("The generated staff email is already used by another account: " + email);
        }

        boolean newAccount = existing == null;
        Staff staff = existing instanceof Staff existingStaff ? existingStaff : new Staff();
        staff.setFullName(facility.getName() + " Staff");
        staff.setEmail(email);
        staff.setPhone("+94770000000");
        if (newAccount) {
            staff.setPassword(passwordEncoder.encode(rawPassword));
        }
        staff.setRole(staffRole);
        staff.setStatus(facility.getStatus() == ParkingFacility.FacilityStatus.INACTIVE
                ? User.UserStatus.INACTIVE
                : User.UserStatus.ACTIVE);
        staff.setEmployeeId("FAC-" + facility.getId());
        staff.setShift("DAY");
        staff.setShiftHours("06:00 - 23:00");
        staff.setAssignedFacility(facility);
        staffRepository.save(staff);

        return new StaffCredentials(facility.getId(), facility.getName(), email, rawPassword);
    }

    public List<StaffCredentials> ensureAccountsForAllFacilities() {
        return facilityRepository.findAll().stream()
                .map(this::ensureStaffAccount)
                .toList();
    }

    public static String emailFor(Long facilityId) {
        return "facility" + facilityId + STAFF_EMAIL_DOMAIN;
    }

    public static String passwordFor(Long facilityId) {
        return STAFF_PASSWORD_PREFIX + facilityId;
    }

    public record StaffCredentials(Long facilityId, String facilityName, String email, String password) {
    }
}
