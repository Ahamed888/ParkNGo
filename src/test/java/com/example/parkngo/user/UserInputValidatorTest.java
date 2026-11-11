package com.example.parkngo.user;

import com.example.parkngo.util.UserInputValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserInputValidatorTest {

    @Test
    void acceptsRealisticNamesAndNormalizesWhitespace() {
        assertTrue(UserInputValidator.isValidName("  Anne-Marie O'Neil  "));
        assertTrue(UserInputValidator.isValidName("සමන් පෙරේරා"));
        assertEquals("Anne-Marie O'Neil", UserInputValidator.cleanName("  Anne-Marie   O'Neil "));
        assertFalse(UserInputValidator.isValidName("   "));
    }

    @Test
    void validatesAndNormalizesSriLankanMobileNumbers() {
        assertTrue(UserInputValidator.isValidSriLankanMobile("077 123 4567"));
        assertTrue(UserInputValidator.isValidSriLankanMobile("+94 77 123 4567"));
        assertEquals("0771234567", UserInputValidator.cleanPhone("077-123-4567"));
        assertFalse(UserInputValidator.isValidSriLankanMobile("0112345678"));
        assertFalse(UserInputValidator.isValidSriLankanMobile("771234567"));
    }

    @Test
    void enforcesAccountPasswordRequirements() {
        assertTrue(UserInputValidator.isStrongPassword("ParkNGo!2026"));
        assertFalse(UserInputValidator.isStrongPassword("password"));
        assertFalse(UserInputValidator.isStrongPassword("SHORT1!"));
    }

    @Test
    void validatesAndNormalizesEmail() {
        assertTrue(UserInputValidator.isValidEmail(" User.Name+parking@Example.COM "));
        assertEquals("user.name+parking@example.com", UserInputValidator.cleanEmail(" User.Name+parking@Example.COM "));
        assertFalse(UserInputValidator.isValidEmail("not-an-email"));
    }
}
