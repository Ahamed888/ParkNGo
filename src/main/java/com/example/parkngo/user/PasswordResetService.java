package com.example.parkngo.user;

import com.example.parkngo.feedback.EmailService;
import com.example.parkngo.util.UserInputValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
@Transactional
public class PasswordResetService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserService userService;
    private final ObjectProvider<EmailService> emailServiceProvider;
    private final String resetBaseUrl;
    private final long tokenExpiryMinutes;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                UserService userService,
                                ObjectProvider<EmailService> emailServiceProvider,
                                @Value("${app.password-reset.base-url:http://localhost:8080}") String resetBaseUrl,
                                @Value("${app.password-reset.token-expiry-minutes:60}") long tokenExpiryMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userService = userService;
        this.emailServiceProvider = emailServiceProvider;
        this.resetBaseUrl = resetBaseUrl;
        this.tokenExpiryMinutes = tokenExpiryMinutes;
    }

    public void requestPasswordReset(String submittedEmail) {
        String email = UserInputValidator.cleanEmail(submittedEmail);
        if (!UserInputValidator.isValidEmail(email)) {
            throw new IllegalArgumentException("Please provide a valid email address.");
        }

        Optional<User> account = userRepository.findByEmail(email)
                .filter(user -> user.getStatus() != User.UserStatus.DELETED);
        if (account.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        User user = account.get();
        tokenRepository.invalidateUnusedTokensForUser(user.getId(), now);

        String rawToken = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(rawToken));
        resetToken.setCreatedAt(now);
        resetToken.setExpiresAt(now.plusMinutes(tokenExpiryMinutes));
        tokenRepository.save(resetToken);

        EmailService emailService = emailServiceProvider.getIfAvailable();
        if (emailService != null) {
            String resetLink = UriComponentsBuilder.fromUriString(resetBaseUrl)
                    .path("/reset-password")
                    .queryParam("token", rawToken)
                    .build()
                    .encode()
                    .toUriString();
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink, tokenExpiryMinutes);
        }
    }

    @Transactional(readOnly = true)
    public boolean isTokenValid(String rawToken) {
        if (!isPlausibleToken(rawToken)) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        return tokenRepository.findByTokenHash(hashToken(rawToken))
                .map(token -> token.isUsableAt(now))
                .orElse(false);
    }

    public void resetPassword(String rawToken, String newPassword, String confirmPassword) throws Exception {
        if (!isPlausibleToken(rawToken)) {
            throw new InvalidPasswordResetTokenException();
        }

        PasswordResetToken token = tokenRepository.findByTokenHashForUpdate(hashToken(rawToken))
                .orElseThrow(InvalidPasswordResetTokenException::new);
        LocalDateTime now = LocalDateTime.now();
        if (!token.isUsableAt(now)) {
            throw new InvalidPasswordResetTokenException();
        }
        if (!UserInputValidator.isStrongPassword(newPassword)) {
            throw new IllegalArgumentException("Password must be 8-72 characters and include uppercase, lowercase, a number, and a special character.");
        }
        if (confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match. Please re-enter.");
        }

        userService.resetPassword(token.getUser().getId(), newPassword);
        token.setUsedAt(now);
        tokenRepository.save(token);
    }

    static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isPlausibleToken(String rawToken) {
        return rawToken != null && !rawToken.isBlank() && rawToken.length() <= 200;
    }
}
