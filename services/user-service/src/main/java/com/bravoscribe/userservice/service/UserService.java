package com.bravoscribe.userservice.service;

import com.bravoscribe.userservice.config.JwtProperties;
import com.bravoscribe.userservice.dto.*;
import com.bravoscribe.userservice.entity.PasswordResetToken;
import com.bravoscribe.userservice.entity.User;
import com.bravoscribe.userservice.exception.UserError;
import com.bravoscribe.userservice.exception.UserServiceException;
import com.bravoscribe.userservice.kafka.UserEventProducer;
import com.bravoscribe.userservice.util.TokenHasher;
import com.bravoscribe.userservice.repository.PasswordResetTokenRepository;
import com.bravoscribe.userservice.repository.RefreshTokenRepository;
import com.bravoscribe.userservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordResetTokenRepository resetTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProps;
    private final UserEventProducer eventProducer;

    public UserService(UserRepository userRepo,
                       RefreshTokenRepository refreshTokenRepo,
                       PasswordResetTokenRepository resetTokenRepo,
                       PasswordEncoder passwordEncoder,
                       JwtProperties jwtProps,
                       UserEventProducer eventProducer) {
        this.userRepo = userRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.resetTokenRepo = resetTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtProps = jwtProps;
        this.eventProducer = eventProducer;
    }

    public UserResponse getMe(UUID userId) {
        User user = requireUser(userId);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = requireUser(userId);
        // Only name is accepted — role and active are never touched here (mass assignment protection)
        user.setName(req.name());
        return UserResponse.from(userRepo.save(user));
    }

    @Transactional
    public PreferencesResponse updatePreferences(UUID userId, UpdatePreferencesRequest req) {
        User user = requireUser(userId);
        user.setReminderTime(req.reminderTime());
        if (req.weeklySummaryEnabled() != null) {
            user.setWeeklySummaryEnabled(req.weeklySummaryEnabled());
        }
        userRepo.save(user);
        return new PreferencesResponse(user.getReminderTime(), user.isWeeklySummaryEnabled());
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest req) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new UserServiceException(new UserError.WrongCurrentPassword());
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);
        // Invalidate all refresh tokens after password change
        refreshTokenRepo.revokeAllByUserId(userId);
        log.info("Password changed: userId={}", userId);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        // Always return 204 — never reveal whether email is registered (anti-enumeration)
        userRepo.findByEmail(email).ifPresent(user -> {
            String rawToken = generateRawToken();
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(jwtProps.passwordResetExpiryMinutes()));

            var resetToken = new PasswordResetToken();
            resetToken.setToken(TokenHasher.hash(rawToken));
            resetToken.setUserId(user.getId());
            resetToken.setExpiresAt(expiresAt);
            resetTokenRepo.save(resetToken);

            eventProducer.publishPasswordResetRequested(user.getId(), user.getEmail(), rawToken, expiresAt);
            log.info("Password reset requested: userId={}", user.getId());
        });
    }

    @Transactional
    public void confirmPasswordReset(ResetPasswordRequest req) {
        String hashedToken = TokenHasher.hash(req.token());
        PasswordResetToken resetToken = resetTokenRepo.findByToken(hashedToken)
                .orElseThrow(() -> new UserServiceException(new UserError.InvalidPasswordResetToken()));

        if (resetToken.isUsed()) {
            throw new UserServiceException(new UserError.PasswordResetTokenAlreadyUsed());
        }
        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new UserServiceException(new UserError.PasswordResetTokenExpired());
        }

        User user = requireUser(resetToken.getUserId());
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepo.save(user);

        resetToken.setUsed(true);
        resetTokenRepo.save(resetToken);

        // Revoke all refresh tokens for security
        refreshTokenRepo.revokeAllByUserId(user.getId());
        log.info("Password reset confirmed: userId={}", user.getId());
    }

    @Transactional
    public void deactivateUser(UUID targetUserId) {
        User user = requireUser(targetUserId);
        user.setActive(false);
        userRepo.save(user);
        refreshTokenRepo.revokeAllByUserId(targetUserId);
        eventProducer.publishUserDeactivated(targetUserId);
        log.info("User deactivated: userId={}", targetUserId);
    }

    public Page<UserResponse> getUserPage(Pageable pageable) {
        Pageable capped = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
                Sort.by("createdAt").descending()
        );
        return userRepo.findAll(capped).map(UserResponse::from);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanExpiredPasswordResetTokens() {
        resetTokenRepo.deleteExpiredBefore(Instant.now());
        log.info("Cleaned expired password reset tokens");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private User requireUser(UUID userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> new UserServiceException(new UserError.UserNotFound(userId.toString())));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
