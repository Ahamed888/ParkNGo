package com.example.parkngo.config;

import com.example.parkngo.facility.ParkingFacilityRepository;
import com.example.parkngo.space.ParkingSpaceRepository;
import com.example.parkngo.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SubscriptionSchemaMigrationTest {
    @Test
    void schemaFailureStopsStartupInsteadOfLeavingPurchasesBroken() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Migration failed");
        doThrow(failure).when(jdbc).execute(anyString());
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> initializer(jdbc).synchronizeSubscriptionStatusConstraint());
        assertSame(failure, error.getCause());
        assertTrue(error.getMessage().contains("Subscription.Status"));
    }

    private DataInitializer initializer(JdbcTemplate jdbc) {
        return new DataInitializer(mock(RoleRepository.class), mock(StaffRepository.class),
                mock(CustomerRepository.class), mock(AdminRepository.class), mock(ParkingFacilityRepository.class),
                mock(ParkingSpaceRepository.class), mock(BCryptPasswordEncoder.class), jdbc);
    }
}
