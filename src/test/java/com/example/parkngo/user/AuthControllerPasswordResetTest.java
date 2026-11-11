package com.example.parkngo.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerPasswordResetTest {
    private PasswordResetService passwordResetService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        passwordResetService = mock(PasswordResetService.class);
        controller = new AuthController(mock(UserService.class), mock(UserRepository.class), passwordResetService);
    }

    @Test
    void existingAndNonexistentAccountOutcomesUseSameNeutralRedirect() {
        ConcurrentModel existingModel = new ConcurrentModel();
        assertEquals("redirect:/forgot-password?sent",
                controller.requestPasswordReset("driver@example.com", existingModel));
        verify(passwordResetService).requestPasswordReset("driver@example.com");

        doThrow(new RuntimeException("delivery unavailable"))
                .when(passwordResetService).requestPasswordReset("missing@example.com");
        ConcurrentModel missingModel = new ConcurrentModel();
        assertEquals("redirect:/forgot-password?sent",
                controller.requestPasswordReset("missing@example.com", missingModel));
    }

    @Test
    void malformedEmailUsesInlineValidation() {
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("auth/forgot-password", controller.requestPasswordReset("not-an-email", model));

        assertTrue(((Map<?, ?>) model.getAttribute("fieldErrors")).containsKey("email"));
    }
}
