package com.example.parkngo.vehicle;

import com.example.parkngo.user.Role;
import com.example.parkngo.user.RoleRepository;
import com.example.parkngo.user.User;
import com.example.parkngo.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VehicleServiceTest {
    @Autowired
    private VehicleService vehicleService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        vehicleRepository.deleteAll();

        Role role = roleRepository.findByName("ROLE_CUSTOMER")
                .or(() -> roleRepository.findByName("CUSTOMER"))
                .orElseGet(() -> roleRepository.save(new Role(null, "ROLE_CUSTOMER", "Customer Role")));

        testUser = userRepository.findByEmail("test@example.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("test@example.com");
            u.setFullName("Test User");
            u.setPhone("+1-800-TEST");
            u.setPassword(new BCryptPasswordEncoder().encode("password"));
            u.setRole(role);
            return userRepository.save(u);
        });

        otherUser = userRepository.findByEmail("other@example.com").orElseGet(() -> {
            User u = new User();
            u.setEmail("other@example.com");
            u.setFullName("Other User");
            u.setPhone("+1-800-OTHER");
            u.setPassword(new BCryptPasswordEncoder().encode("password"));
            u.setRole(role);
            return userRepository.save(u);
        });
    }

    @Test
    void testRegisterVehicle() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1001");
        vehicle.setVehicleType("SEDAN");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Camry");
        vehicle.setColor("Silver");
        vehicle.setCustomer(testUser);

        Vehicle created = vehicleService.registerVehicle(vehicle);
        assertNotNull(created.getId());
        assertEquals("WP CA-1001", created.getRegistrationNumber());
        assertEquals(Vehicle.VehicleStatus.ACTIVE, created.getStatus());
    }

    @Test
    void testDuplicateRegistrationPrevention() throws Exception {
        Vehicle vehicle1 = new Vehicle();
        vehicle1.setRegistrationNumber("WP CA-1002");
        vehicle1.setVehicleType("SEDAN");
        vehicle1.setBrand("Honda");
        vehicle1.setModel("Accord");
        vehicle1.setColor("Blue");
        vehicle1.setCustomer(testUser);
        vehicleService.registerVehicle(vehicle1);

        Vehicle vehicle2 = new Vehicle();
        vehicle2.setRegistrationNumber("wp ca-1002"); // lowercase test to ensure uppercase normalization
        vehicle2.setVehicleType("SEDAN");
        vehicle2.setBrand("Toyota");
        vehicle2.setModel("Camry");
        vehicle2.setColor("Red");
        vehicle2.setCustomer(testUser);

        assertThrows(IllegalArgumentException.class, () -> vehicleService.registerVehicle(vehicle2));
    }

    @Test
    void testGetCustomerVehicles() throws Exception {
        Vehicle vehicle1 = new Vehicle();
        vehicle1.setRegistrationNumber("WP CA-1003");
        vehicle1.setVehicleType("SEDAN");
        vehicle1.setBrand("Toyota");
        vehicle1.setModel("Camry");
        vehicle1.setColor("Silver");
        vehicle1.setCustomer(testUser);
        vehicleService.registerVehicle(vehicle1);

        Vehicle vehicle2 = new Vehicle();
        vehicle2.setRegistrationNumber("WP CA-1004");
        vehicle2.setVehicleType("SUV");
        vehicle2.setBrand("Honda");
        vehicle2.setModel("CR-V");
        vehicle2.setColor("Black");
        vehicle2.setCustomer(testUser);
        vehicleService.registerVehicle(vehicle2);

        List<Vehicle> vehicles = vehicleService.getCustomerVehicles(testUser.getId());
        assertEquals(2, vehicles.size());
    }

    @Test
    void testUpdateVehicle() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1005");
        vehicle.setVehicleType("SEDAN");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Corolla");
        vehicle.setColor("White");
        vehicle.setCustomer(testUser);
        Vehicle saved = vehicleService.registerVehicle(vehicle);

        saved.setBrand("Toyota");
        saved.setModel("Camry");
        saved.setColor("Midnight Black");
        saved.setVehicleType("SUV");

        Vehicle updated = vehicleService.updateVehicle(saved, testUser.getId());
        assertEquals("Camry", updated.getModel());
        assertEquals("Midnight Black", updated.getColor());
        assertEquals("SUV", updated.getVehicleType());
    }

    @Test
    void testUpdateVehicleDuplicatePlatePrevention() throws Exception {
        Vehicle v1 = new Vehicle();
        v1.setRegistrationNumber("WP CA-1006");
        v1.setVehicleType("SEDAN");
        v1.setBrand("Nissan");
        v1.setModel("Sunny");
        v1.setCustomer(testUser);
        vehicleService.registerVehicle(v1);

        Vehicle v2 = new Vehicle();
        v2.setRegistrationNumber("WP CA-1007");
        v2.setVehicleType("SEDAN");
        v2.setBrand("Nissan");
        v2.setModel("March");
        v2.setCustomer(testUser);
        Vehicle savedV2 = vehicleService.registerVehicle(v2);

        savedV2.setRegistrationNumber("WP CA-1006"); // conflict with v1
        assertThrows(IllegalArgumentException.class, () -> vehicleService.updateVehicle(savedV2, testUser.getId()));
    }

    @Test
    void testCustomerOwnershipIsolation() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1008");
        vehicle.setVehicleType("SEDAN");
        vehicle.setBrand("Tesla");
        vehicle.setModel("Model 3");
        vehicle.setColor("Red");
        vehicle.setCustomer(testUser);
        Vehicle registered = vehicleService.registerVehicle(vehicle);

        // Other user should not be able to get, update or delete this vehicle
        assertThrows(Exception.class, () -> vehicleService.getVehicleByIdAndCustomer(registered.getId(), otherUser.getId()));
        assertThrows(Exception.class, () -> vehicleService.deleteVehicle(registered.getId(), otherUser.getId()));
    }

    @Test
    void testDeleteVehicle() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1009");
        vehicle.setVehicleType("SEDAN");
        vehicle.setBrand("Toyota");
        vehicle.setModel("Camry");
        vehicle.setColor("Silver");
        vehicle.setCustomer(testUser);
        Vehicle registered = vehicleService.registerVehicle(vehicle);

        vehicleService.deleteVehicle(registered.getId(), testUser.getId());
        assertThrows(Exception.class, () -> vehicleService.getVehicleById(registered.getId()));
    }

    @Test
    void testIsVehicleRegistered() throws Exception {
        Vehicle vehicle = new Vehicle();
        vehicle.setRegistrationNumber("WP CA-1010");
        vehicle.setVehicleType("ELECTRIC");
        vehicle.setBrand("Tesla");
        vehicle.setModel("Model Y");
        vehicle.setCustomer(testUser);
        vehicleService.registerVehicle(vehicle);

        assertTrue(vehicleService.isVehicleRegistered("WP CA-1010"));
        assertTrue(vehicleService.isVehicleRegistered("wp ca-1010"));
        assertFalse(vehicleService.isVehicleRegistered("NON-EXISTENT"));
    }
}
