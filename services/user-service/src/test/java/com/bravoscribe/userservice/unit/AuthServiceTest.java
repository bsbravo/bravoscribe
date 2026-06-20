package com.bravoscribe.userservice.unit;

import com.bravoscribe.userservice.config.JwtProperties;
import com.bravoscribe.userservice.dto.LoginRequest;
import com.bravoscribe.userservice.dto.RegisterRequest;
import com.bravoscribe.userservice.entity.RefreshToken;
import com.bravoscribe.userservice.entity.Role;
import com.bravoscribe.userservice.entity.User;
import com.bravoscribe.userservice.exception.UserError;
import com.bravoscribe.userservice.exception.UserServiceException;
import com.bravoscribe.userservice.kafka.UserEventProducer;
import com.bravoscribe.userservice.repository.RefreshTokenRepository;
import com.bravoscribe.userservice.repository.UserRepository;
import com.bravoscribe.userservice.service.AuthService;
import com.bravoscribe.userservice.service.JwtService;
import com.bravoscribe.userservice.util.TokenHasher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserEventProducer eventProducer;

    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    AuthService authService;

    private static final JwtProperties PROPS = new JwtProperties(null, null, 900L, 30L, 15L);
    private static final String MOCK_HASH = "$2a$12$mockHashValue";
    private static final String MOCK_JWT = "eyJhbGciOiJSUzI1NiJ9.mock.jwt";

    @BeforeEach
    void setUp() {
        lenient().when(passwordEncoder.encode(any())).thenReturn(MOCK_HASH);
        authService = new AuthService(userRepo, refreshTokenRepo, passwordEncoder, jwtService, PROPS, eventProducer, meterRegistry);
        ReflectionTestUtils.setField(authService, "cookieSecure", false);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    void register_success_returns_access_token_and_publishes_event() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepo.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        when(jwtService.issueAccessToken(any(), eq(Role.USER))).thenReturn(MOCK_JWT);

        var req = new RegisterRequest("Test User", "new@test.com", "password123");
        var result = authService.register(req);

        assertThat(result.accessToken()).isEqualTo(MOCK_JWT);
        assertThat(result.cookie().getName()).isEqualTo("refreshToken");
        verify(eventProducer).publishUserRegistered(any(), eq("new@test.com"));
        verify(refreshTokenRepo).save(any());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_duplicate_email_throws_email_already_exists() {
        when(userRepo.existsByEmail("taken@test.com")).thenReturn(true);

        var req = new RegisterRequest("User", "taken@test.com", "password123");
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.EmailAlreadyExists.class));
    }

    // ── login ─────────────────────────────────────────────────────────────────

    @Test
    void login_success_returns_token_and_sets_cookie() {
        User user = activeUser();
        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
        when(jwtService.issueAccessToken(user.getId(), Role.USER)).thenReturn(MOCK_JWT);

        var req = new LoginRequest("user@test.com", "password123");
        var result = authService.login(req);

        assertThat(result.accessToken()).isEqualTo(MOCK_JWT);
        assertThat(result.cookie().getName()).isEqualTo("refreshToken");
        assertThat(result.cookie().isHttpOnly()).isTrue();
        assertThat(result.cookie().getSameSite()).isEqualTo("Strict");
        verify(refreshTokenRepo).save(any());
    }

    @Test
    void login_wrong_password_throws_invalid_credentials() {
        User user = activeUser();
        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        var req = new LoginRequest("user@test.com", "wrongpassword");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidCredentials.class));
    }

    @Test
    void login_user_not_found_throws_invalid_credentials_and_still_runs_bcrypt() {
        // Anti-enumeration: same error, BCrypt must still run
        when(userRepo.findByEmail("nobody@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        var req = new LoginRequest("nobody@test.com", "password123");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidCredentials.class));

        // Verify BCrypt still ran (dummy hash used) — constant-time guarantee
        verify(passwordEncoder).matches(eq("password123"), any());
    }

    @Test
    void login_deactivated_user_throws_user_deactivated() {
        User user = activeUser();
        user.setActive(false);
        when(userRepo.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        var req = new LoginRequest("user@test.com", "password123");
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.UserDeactivated.class));
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    @Test
    void refresh_valid_token_rotates_and_returns_new_jwt() {
        User user = activeUser();
        String rawToken = "some-raw-token-value";
        String hashed = TokenHasher.hash(rawToken);

        RefreshToken stored = validRefreshToken(hashed, user.getId());
        when(refreshTokenRepo.findByToken(hashed)).thenReturn(Optional.of(stored));
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(jwtService.issueAccessToken(user.getId(), Role.USER)).thenReturn(MOCK_JWT);

        var result = authService.refresh(rawToken);

        assertThat(result.accessToken()).isEqualTo(MOCK_JWT);
        assertThat(stored.isRevoked()).isTrue();
        verify(refreshTokenRepo, times(2)).save(any()); // revoked old + saved new
    }

    @Test
    void refresh_revoked_token_triggers_full_revocation() {
        String rawToken = "revoked-raw-token";
        String hashed = TokenHasher.hash(rawToken);
        UUID userId = UUID.randomUUID();

        RefreshToken revoked = validRefreshToken(hashed, userId);
        revoked.setRevoked(true);
        when(refreshTokenRepo.findByToken(hashed)).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidRefreshToken.class));

        verify(refreshTokenRepo).revokeAllByUserId(userId);
    }

    @Test
    void refresh_expired_token_throws_invalid_refresh_token() {
        String rawToken = "expired-raw-token";
        String hashed = TokenHasher.hash(rawToken);

        RefreshToken expired = validRefreshToken(hashed, UUID.randomUUID());
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepo.findByToken(hashed)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(rawToken))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidRefreshToken.class));
    }

    @Test
    void refresh_null_token_throws_invalid_refresh_token() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(UserServiceException.class)
                .satisfies(ex -> assertThat(((UserServiceException) ex).getError())
                        .isInstanceOf(UserError.InvalidRefreshToken.class));
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_known_token_gets_revoked() {
        String rawToken = "valid-token";
        String hashed = TokenHasher.hash(rawToken);
        RefreshToken stored = validRefreshToken(hashed, UUID.randomUUID());
        when(refreshTokenRepo.findByToken(hashed)).thenReturn(Optional.of(stored));

        var cookie = authService.logout(rawToken);

        assertThat(stored.isRevoked()).isTrue();
        assertThat(cookie.getName()).isEqualTo("refreshToken");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0L);
    }

    @Test
    void logout_unknown_token_returns_clear_cookie_without_error() {
        when(refreshTokenRepo.findByToken(any())).thenReturn(Optional.empty());

        var cookie = authService.logout("unknown-token");
        assertThat(cookie.getMaxAge().getSeconds()).isEqualTo(0L);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User activeUser() {
        var user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setEmail("user@test.com");
        user.setPasswordHash(MOCK_HASH);
        user.setRole(Role.USER);
        user.setActive(true);
        user.setName("Test User");
        return user;
    }

    private RefreshToken validRefreshToken(String token, UUID userId) {
        var rt = new RefreshToken();
        ReflectionTestUtils.setField(rt, "id", UUID.randomUUID());
        rt.setToken(token);
        rt.setUserId(userId);
        rt.setExpiresAt(Instant.now().plusSeconds(86400));
        rt.setRevoked(false);
        return rt;
    }
}
