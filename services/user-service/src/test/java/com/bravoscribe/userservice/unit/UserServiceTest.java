package com.bravoscribe.userservice.unit;

import com.bravoscribe.userservice.config.JwtProperties;
import com.bravoscribe.userservice.dto.ChangePasswordRequest;
import com.bravoscribe.userservice.dto.ResetPasswordRequest;
import com.bravoscribe.userservice.dto.UpdateProfileRequest;
import com.bravoscribe.userservice.entity.PasswordResetToken;
import com.bravoscribe.userservice.entity.Role;
import com.bravoscribe.userservice.entity.User;
import com.bravoscribe.userservice.exception.UserError;
import com.bravoscribe.userservice.exception.UserServiceException;
import com.bravoscribe.userservice.kafka.UserEventProducer;
import com.bravoscribe.userservice.repository.PasswordResetTokenRepository;
import com.bravoscribe.userservice.repository.RefreshTokenRepository;
import com.bravoscribe.userservice.repository.UserRepository;
import com.bravoscribe.userservice.service.UserService;
import com.bravoscribe.userservice.util.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock PasswordResetTokenRepository resetTokenRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserEventProducer eventProducer;

    UserService userService;

    private static final JwtProperties PROPS = new JwtProperties(null, null, 900L, 30L, 15L);

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, refreshTokenRepo, resetTokenRepo,
                passwordEncoder, PROPS, eventProducer);
    }

    // ── getMe ─────────────────────────────────────────────────────────────────

    @Test
    void getMe_returns_user_response() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var response = userService.getMe(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo(user.getEmail());
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void getMe_unknown_user_throws_user_not_found() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMe(id))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.UserNotFound.class));
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_only_updates_name() {
        User user = makeUser(UUID.randomUUID());
        user.setRole(Role.USER);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenReturn(user);

        var req = new UpdateProfileRequest("New Name");
        var response = userService.updateProfile(user.getId(), req);

        assertThat(response.name()).isEqualTo("New Name");
        // Role and active state must not be altered
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isActive()).isTrue();
    }

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_success_updates_hash_and_revokes_tokens() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("currentPass", user.getPasswordHash())).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$12$newHash");

        userService.changePassword(user.getId(), new ChangePasswordRequest("currentPass", "newPassword123"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHash");
        verify(refreshTokenRepo).revokeAllByUserId(user.getId());
    }

    @Test
    void changePassword_wrong_current_throws_wrong_password() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(user.getId(),
                new ChangePasswordRequest("wrongPass", "newPassword123")))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.WrongCurrentPassword.class));

        verify(userRepo, never()).save(any());
    }

    // ── requestPasswordReset ──────────────────────────────────────────────────

    @Test
    void requestPasswordReset_existing_email_publishes_kafka_event() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        userService.requestPasswordReset(user.getEmail());

        verify(resetTokenRepo).save(any());
        verify(eventProducer).publishPasswordResetRequested(eq(user.getId()), eq(user.getEmail()), any(), any());
    }

    @Test
    void requestPasswordReset_unknown_email_does_nothing_silently() {
        when(userRepo.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        // Must not throw — anti-enumeration
        assertThatCode(() -> userService.requestPasswordReset("ghost@test.com"))
                .doesNotThrowAnyException();

        verify(resetTokenRepo, never()).save(any());
        verify(eventProducer, never()).publishPasswordResetRequested(any(), any(), any(), any());
    }

    // ── confirmPasswordReset ──────────────────────────────────────────────────

    @Test
    void confirmPasswordReset_success_updates_password_and_revokes_tokens() {
        User user = makeUser(UUID.randomUUID());
        String rawToken = "some-valid-raw-token-value-123";
        String hashed = TokenHasher.hash(rawToken);

        PasswordResetToken resetToken = validResetToken(hashed, user.getId());
        when(resetTokenRepo.findByToken(hashed)).thenReturn(Optional.of(resetToken));
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass1234")).thenReturn("$2a$12$newHash");

        userService.confirmPasswordReset(new ResetPasswordRequest(rawToken, "newPass1234"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$12$newHash");
        assertThat(resetToken.isUsed()).isTrue();
        verify(refreshTokenRepo).revokeAllByUserId(user.getId());
    }

    @Test
    void confirmPasswordReset_invalid_token_throws() {
        when(resetTokenRepo.findByToken(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.confirmPasswordReset(
                new ResetPasswordRequest("bad-token", "newPass1234")))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidPasswordResetToken.class));
    }

    @Test
    void confirmPasswordReset_expired_token_throws() {
        String rawToken = "expired-raw-token-value-1234";
        String hashed = TokenHasher.hash(rawToken);

        PasswordResetToken resetToken = validResetToken(hashed, UUID.randomUUID());
        resetToken.setExpiresAt(Instant.now().minusSeconds(1));
        when(resetTokenRepo.findByToken(hashed)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> userService.confirmPasswordReset(
                new ResetPasswordRequest(rawToken, "newPass1234")))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.PasswordResetTokenExpired.class));
    }

    @Test
    void confirmPasswordReset_already_used_token_throws() {
        String rawToken = "already-used-raw-token-123456";
        String hashed = TokenHasher.hash(rawToken);

        PasswordResetToken resetToken = validResetToken(hashed, UUID.randomUUID());
        resetToken.setUsed(true);
        when(resetTokenRepo.findByToken(hashed)).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> userService.confirmPasswordReset(
                new ResetPasswordRequest(rawToken, "newPass1234")))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.PasswordResetTokenAlreadyUsed.class));
    }

    // ── deactivateUser ────────────────────────────────────────────────────────

    @Test
    void deactivateUser_sets_inactive_revokes_tokens_and_publishes_event() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        userService.deactivateUser(user.getId());

        assertThat(user.isActive()).isFalse();
        verify(refreshTokenRepo).revokeAllByUserId(user.getId());
        verify(eventProducer).publishUserDeactivated(user.getId());
    }

    @Test
    void deactivateUser_unknown_user_throws_user_not_found() {
        UUID id = UUID.randomUUID();
        when(userRepo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deactivateUser(id))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.UserNotFound.class));
    }

    // ── getUserPage ───────────────────────────────────────────────────────────

    @Test
    void getUserPage_returns_page_of_users() {
        User user = makeUser(UUID.randomUUID());
        when(userRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(user)));

        var page = userService.getUserPage(PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getUserPage_caps_size_at_max_instead_of_throwing() {
        when(userRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        assertThatCode(() -> userService.getUserPage(PageRequest.of(0, 200)))
                .doesNotThrowAnyException();
    }

    // ── cleanExpiredPasswordResetTokens ───────────────────────────────────────

    @Test
    void cleanExpiredPasswordResetTokens_delegates_to_repo() {
        assertThatCode(() -> userService.cleanExpiredPasswordResetTokens())
                .doesNotThrowAnyException();
        verify(resetTokenRepo).deleteExpiredBefore(any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user@test.com");
        user.setName("Test User");
        user.setPasswordHash("$2a$12$existingHash");
        user.setRole(Role.USER);
        user.setActive(true);
        return user;
    }

    private PasswordResetToken validResetToken(String hashedToken, UUID userId) {
        var token = new PasswordResetToken();
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        token.setToken(hashedToken);
        token.setUserId(userId);
        token.setExpiresAt(Instant.now().plusSeconds(900));
        token.setUsed(false);
        return token;
    }
}
