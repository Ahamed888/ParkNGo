package com.example.parkngo.user;

import com.example.parkngo.facility.ParkingFacility;
import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.util.UserInputValidator;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ParkingFacilityRepository parkingFacilityRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                       ParkingFacilityRepository parkingFacilityRepository,
                       BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.parkingFacilityRepository = parkingFacilityRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User registerUser(User user, String roleName) throws Exception {
        return registerUser(user, roleName, null);
    }

    public User registerUser(User user, String roleName, Long facilityId) throws Exception {
        if (user == null || !UserInputValidator.isValidName(user.getFullName())) {
            throw new IllegalArgumentException("Full name must be between 2 and 60 characters and contain valid name characters.");
        }
        if (!UserInputValidator.isValidEmail(user.getEmail())) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (!UserInputValidator.isValidSriLankanMobile(user.getPhone())) {
            throw new IllegalArgumentException("Enter a valid Sri Lankan mobile number, for example 077 123 4567.");
        }
        if (!UserInputValidator.isStrongPassword(user.getPassword())) {
            throw new IllegalArgumentException("Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (user.getShiftHours() != null && user.getShiftHours().length() > 50) {
            throw new IllegalArgumentException("Shift hours must be 50 characters or fewer.");
        }
        user.setFullName(UserInputValidator.cleanName(user.getFullName()));
        user.setEmail(UserInputValidator.cleanEmail(user.getEmail()));
        user.setPhone(UserInputValidator.cleanPhone(user.getPhone()));
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new Exception("An account with this email address already exists.");
        }

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new Exception("Role not found: " + roleName));
        ParkingFacility assignedFacility = requireActiveStaffFacility(roleName, facilityId);

        User userToSave;
        if ("ADMIN".equalsIgnoreCase(roleName)) {
            if (user instanceof Admin) {
                userToSave = user;
            } else {
                Admin admin = new Admin();
                copyUserFields(user, admin);
                userToSave = admin;
            }
        } else if ("STAFF".equalsIgnoreCase(roleName)) {
            if (user instanceof Staff) {
                userToSave = user;
            } else {
                Staff staff = new Staff();
                copyUserFields(user, staff);
                userToSave = staff;
            }
            ((Staff) userToSave).setAssignedFacility(assignedFacility);
        } else {
            if (user instanceof Customer) {
                userToSave = user;
            } else {
                Customer customer = new Customer();
                copyUserFields(user, customer);
                userToSave = customer;
            }
        }

        userToSave.setRole(role);
        userToSave.setPassword(passwordEncoder.encode(user.getPassword()));
        if (userToSave.getStatus() == null) {
            userToSave.setStatus(User.UserStatus.ACTIVE);
        }

        return userRepository.save(userToSave);
    }

    private void copyUserFields(User source, User target) {
        target.setId(source.getId());
        target.setFullName(source.getFullName());
        target.setEmail(source.getEmail());
        target.setPhone(source.getPhone());
        target.setPassword(source.getPassword());
        target.setStatus(source.getStatus());
        target.setShiftHours(source.getShiftHours());
        target.setProfilePicture(source.getProfilePicture());
    }

    public User getUserById(Long id) throws Exception {
        return userRepository.findById(id)
                .orElseThrow(() -> new Exception("User not found with ID: " + id));
    }

    public User getUserByEmail(String email) throws Exception {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new Exception("User not found with email: " + email));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() != User.UserStatus.DELETED)
                .collect(Collectors.toList());
    }

    public List<ParkingFacility> getActiveParkingFacilities() {
        return parkingFacilityRepository.findByStatus(ParkingFacility.FacilityStatus.ACTIVE);
    }

    public ParkingFacility requireActiveStaffFacility(String roleName, Long facilityId) {
        if (!"STAFF".equalsIgnoreCase(roleName)) {
            return null;
        }
        if (facilityId == null) {
            throw new IllegalArgumentException("Select an active parking facility for this staff member.");
        }
        ParkingFacility facility = parkingFacilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("The selected parking facility does not exist."));
        if (facility.getStatus() != ParkingFacility.FacilityStatus.ACTIVE) {
            throw new IllegalArgumentException("The selected parking facility is inactive. Select an active facility.");
        }
        return facility;
    }

    public List<User> getUsersByRole(String roleName) {
        return userRepository.findByRoleName(roleName).stream()
                .filter(u -> u.getStatus() != User.UserStatus.DELETED)
                .collect(Collectors.toList());
    }

    public User updateUser(User user) throws Exception {
        User existing = getUserById(user.getId());
        existing.setFullName(user.getFullName());
        existing.setPhone(user.getPhone());
        if (user.getProfilePicture() != null) {
            existing.setProfilePicture(user.getProfilePicture());
        }
        return userRepository.save(existing);
    }

    public User updateProfile(Long userId, String fullName, String phone, String profilePicture) throws Exception {
        if (!UserInputValidator.isValidName(fullName)) throw new IllegalArgumentException("Enter a valid full name between 2 and 60 characters.");
        if (!UserInputValidator.isValidSriLankanMobile(phone)) throw new IllegalArgumentException("Enter a valid Sri Lankan mobile number, for example 077 123 4567.");
        User existing = getUserById(userId);
        existing.setFullName(UserInputValidator.cleanName(fullName));
        existing.setPhone(UserInputValidator.cleanPhone(phone));
        if (profilePicture != null) {
            existing.setProfilePicture(profilePicture.isBlank() ? null : profilePicture);
        }
        return userRepository.save(existing);
    }

    public User updateProfile(Long userId, String fullName, String phone) throws Exception {
        return updateProfile(userId, fullName, phone, null);
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) throws Exception {
        User existing = getUserById(userId);
        if (!passwordEncoder.matches(oldPassword, existing.getPassword())) {
            throw new Exception("Current password does not match");
        }
        if (!UserInputValidator.isStrongPassword(newPassword)) {
            throw new Exception("New password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        existing.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(existing);
    }

    public void resetPassword(String email, String newPassword) throws Exception {
        User existing = getUserByEmail(email);
        resetPassword(existing, newPassword);
    }

    public void resetPassword(Long userId, String newPassword) throws Exception {
        User existing = getUserById(userId);
        resetPassword(existing, newPassword);
    }

    private void resetPassword(User existing, String newPassword) throws Exception {
        if (!UserInputValidator.isStrongPassword(newPassword)) {
            throw new Exception("Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        existing.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(existing);
    }

    public User adminUpdateUser(Long id, String fullName, String email, String phone, 
                               String roleName, User.UserStatus status, String shiftHours, 
                               String newPassword) throws Exception {
        return adminUpdateUser(id, fullName, email, phone, roleName, status, shiftHours, newPassword, null);
    }

    public User adminUpdateUser(Long id, String fullName, String email, String phone,
                               String roleName, User.UserStatus status, String shiftHours,
                               String newPassword, Long facilityId) throws Exception {
        User existing = getUserById(id);
        if (!UserInputValidator.isValidName(fullName)) throw new IllegalArgumentException("Enter a valid full name between 2 and 60 characters.");
        if (!UserInputValidator.isValidEmail(email)) throw new IllegalArgumentException("Enter a valid email address.");
        if (!UserInputValidator.isValidSriLankanMobile(phone)) throw new IllegalArgumentException("Enter a valid Sri Lankan mobile number, for example 077 123 4567.");
        if (shiftHours != null && shiftHours.length() > 50) throw new IllegalArgumentException("Shift hours must be 50 characters or fewer.");
        ParkingFacility assignedFacility = requireActiveStaffFacility(roleName, facilityId);
        if ("STAFF".equalsIgnoreCase(roleName) && !(existing instanceof Staff)) {
            throw new IllegalArgumentException("This account does not have a staff profile and cannot be assigned to a facility.");
        }
        fullName = UserInputValidator.cleanName(fullName);
        email = UserInputValidator.cleanEmail(email);
        phone = UserInputValidator.cleanPhone(phone);
        
        // If email changed, check uniqueness
        if (!existing.getEmail().equalsIgnoreCase(email)) {
            if (userRepository.existsByEmail(email)) {
                throw new Exception("Email " + email + " is already taken by another account");
            }
            existing.setEmail(email);
        }

        existing.setFullName(fullName);
        existing.setPhone(phone);
        existing.setShiftHours(shiftHours);
        if (status != null) {
            existing.setStatus(status);
        }

        if (roleName != null && !roleName.isBlank()) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new Exception("Role not found: " + roleName));
            existing.setRole(role);
        }

        if ("STAFF".equalsIgnoreCase(roleName)) {
            ((Staff) existing).setAssignedFacility(assignedFacility);
        }

        if (newPassword != null && !newPassword.isBlank()) {
            if (!UserInputValidator.isStrongPassword(newPassword)) {
                throw new Exception("Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
            }
            existing.setPassword(passwordEncoder.encode(newPassword));
        }

        return userRepository.save(existing);
    }

    public User toggleUserStatus(Long id) throws Exception {
        User existing = getUserById(id);
        if (existing.getStatus() == User.UserStatus.ACTIVE) {
            existing.setStatus(User.UserStatus.INACTIVE);
        } else {
            existing.setStatus(User.UserStatus.ACTIVE);
        }
        return userRepository.save(existing);
    }

    public List<User> filterUsers(String roleName, String query) {
        List<User> list = (roleName != null && !roleName.isBlank() && !roleName.equalsIgnoreCase("ALL")) ?
                userRepository.findByRoleName(roleName.toUpperCase()) :
                userRepository.findAll();

        if (query == null || query.isBlank()) {
            return list;
        }

        String q = query.toLowerCase();
        return list.stream()
                .filter(u -> (u.getFullName() != null && u.getFullName().toLowerCase().contains(q)) ||
                             (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                             (u.getPhone() != null && u.getPhone().toLowerCase().contains(q)))
                .collect(Collectors.toList());
    }

    public long countByRole(String roleName) {
        return userRepository.countByRoleName(roleName);
    }

    public void deleteUser(Long id) throws Exception {
        User user = getUserById(id);
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (Exception e) {
            // Cannot hard-delete due to foreign key constraints (e.g. reservations, payments, vehicles)
            // Soft delete user instead
            user.setStatus(User.UserStatus.DELETED);
            user.setEmail("deleted_" + System.currentTimeMillis() + "_" + user.getEmail());
            userRepository.save(user);
        }
    }

    public boolean validatePassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
