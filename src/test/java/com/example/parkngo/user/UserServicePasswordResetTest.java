package com.example.parkngo.user;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServicePasswordResetTest {
    @Test
    void successfulResetReplacesOldPasswordWithEncodedNewPassword() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = new User();
        user.setId(7L);
        user.setPassword(encoder.encode("OldSecure!2025"));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        UserService userService = new UserService(userRepository, mock(RoleRepository.class),
                mock(com.example.parkngo.facility.ParkingFacilityRepository.class), encoder);
        userService.resetPassword(7L, "NewSecure!2026");

        assertFalse(encoder.matches("OldSecure!2025", user.getPassword()));
        assertTrue(encoder.matches("NewSecure!2026", user.getPassword()));
        verify(userRepository).save(user);
    }
}
