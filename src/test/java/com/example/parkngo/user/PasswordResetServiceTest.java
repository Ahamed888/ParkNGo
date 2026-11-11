package com.example.parkngo.user;

import com.example.parkngo.feedback.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock UserService userService;
    @Mock ObjectProvider<EmailService> emailServiceProvider;
    @Mock EmailService emailService;

    private PasswordResetService passwordResetService;
    private User user;

    @BeforeEach
    void setUp() {
        passwordResetService = new PasswordResetService(
                userRepository, tokenRepository, userService, emailServiceProvider,
                "https://parkngo.example", 60);
        user = new User();
        user.setId(42L);
        user.setEmail("driver@example.com");
        user.setStatus(User.UserStatus.ACTIVE);
    }

    @Test
    void existingEmailCreatesHashedSecureTokenAndSendsResetLink() {
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(user));
        when(emailServiceProvider.getIfAvailable()).thenReturn(emailService);

        passwordResetService.requestPasswordReset(" Driver@Example.com ");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken stored = tokenCaptor.getValue();
        assertEquals(64, stored.getTokenHash().length());
        assertTrue(stored.getExpiresAt().isAfter(stored.getCreatedAt()));
        assertEquals(60, java.time.Duration.between(stored.getCreatedAt(), stored.getExpiresAt()).toMinutes());

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("driver@example.com"), linkCaptor.capture(), eq(60L));
        String rawToken = linkCaptor.getValue().substring(linkCaptor.getValue().indexOf("token=") + 6);
        assertTrue(rawToken.length() >= 43);
        assertNotEquals(rawToken, stored.getTokenHash());
        assertEquals(stored.getTokenHash(), PasswordResetService.hashToken(rawToken));
    }

    @Test
    void nonexistentEmailCompletesWithoutCreatingTokenOrEmail() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        passwordResetService.requestPasswordReset("missing@example.com");

        verify(tokenRepository, never()).save(any());
        verify(emailServiceProvider, never()).getIfAvailable();
    }

    @Test
    void secondRequestInvalidatesOlderUnusedTokenBeforeCreatingReplacement() {
        when(userRepository.findByEmail("driver@example.com")).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset("driver@example.com");
        passwordResetService.requestPasswordReset("driver@example.com");

        verify(tokenRepository, times(2)).invalidateUnusedTokensForUser(eq(42L), any(LocalDateTime.class));
        verify(tokenRepository, times(2)).save(any(PasswordResetToken.class));
    }

    @Test
    void validTokenResetsPasswordAndBecomesSingleUse() throws Exception {
        String rawToken = "valid-secure-reset-token-value-1234567890";
        PasswordResetToken token = usableToken(rawToken);
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken)))
                .thenReturn(Optional.of(token));

        passwordResetService.resetPassword(rawToken, "NewSecure!2026", "NewSecure!2026");

        verify(userService).resetPassword(42L, "NewSecure!2026");
        assertTrue(token.getUsedAt() != null);
        verify(tokenRepository).save(token);

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordResetService.resetPassword(rawToken, "AnotherSecure!2026", "AnotherSecure!2026"));
    }

    @Test
    void expiredAndInvalidTokensAreRejected() throws Exception {
        String expiredRawToken = "expired-secure-reset-token-value-123456789";
        PasswordResetToken expired = usableToken(expiredRawToken);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(expiredRawToken)))
                .thenReturn(Optional.of(expired));

        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordResetService.resetPassword(expiredRawToken, "NewSecure!2026", "NewSecure!2026"));

        String invalidRawToken = "unknown-secure-reset-token-value-123456789";
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(invalidRawToken)))
                .thenReturn(Optional.empty());
        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordResetService.resetPassword(invalidRawToken, "NewSecure!2026", "NewSecure!2026"));
        verify(userService, never()).resetPassword(anyLong(), any());
    }

    @Test
    void weakPasswordAndConfirmationMismatchAreRejected() throws Exception {
        String rawToken = "valid-secure-reset-token-value-1234567890";
        PasswordResetToken token = usableToken(rawToken);
        when(tokenRepository.findByTokenHashForUpdate(PasswordResetService.hashToken(rawToken)))
                .thenReturn(Optional.of(token));

        assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(rawToken, "password", "password"));
        assertThrows(IllegalArgumentException.class,
                () -> passwordResetService.resetPassword(rawToken, "NewSecure!2026", "Different!2026"));
        assertNull(token.getUsedAt());
        verify(userService, never()).resetPassword(anyLong(), any());
    }

    private PasswordResetToken usableToken(String rawToken) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(PasswordResetService.hashToken(rawToken));
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return token;
    }
}
