package com.bravoscribe.userservice.service;

import com.bravoscribe.userservice.config.JwtProperties;
import com.bravoscribe.userservice.dto.LoginRequest;
import com.bravoscribe.userservice.dto.RegisterRequest;
import com.bravoscribe.userservice.util.TokenHasher;
import com.bravoscribe.userservice.entity.RefreshToken;
import com.bravoscribe.userservice.entity.Role;
import com.bravoscribe.userservice.entity.User;
import com.bravoscribe.userservice.exception.UserError;
import com.bravoscribe.userservice.exception.UserServiceException;
import com.bravoscribe.userservice.kafka.UserEventProducer;
import com.bravoscribe.userservice.repository.RefreshTokenRepository;
import com.bravoscribe.userservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/users";

    private final UserRepository userRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProps;
    private final UserEventProducer eventProducer;
    private final MeterRegistry meterRegistry;

    // Pre-computed dummy hash — ensures BCrypt always runs regardless of whether user exists
    private final String dummyHash;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    public AuthService(UserRepository userRepo,
                       RefreshTokenRepository refreshTokenRepo,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       JwtProperties jwtProps,
                       UserEventProducer eventProducer,
                       MeterRegistry meterRegistry) {
        this.userRepo = userRepo;
        this.refreshTokenRepo = refreshTokenRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProps = jwtProps;
        this.eventProducer = eventProducer;
        this.meterRegistry = meterRegistry;
        this.dummyHash = passwordEncoder.encode("dummy-anti-timing-bravoscribe");
    }

    @Transactional
    public LoginResult register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new UserServiceException(new UserError.EmailAlreadyExists(req.email()));
        }

        var user = new User();
        user.setName(req.name());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.USER);
        user.setActive(true);
        userRepo.save(user);

        String rawToken = generateRawToken();
        saveRefreshToken(user.getId(), rawToken);

        eventProducer.publishUserRegistered(user.getId(), user.getEmail());
        meterRegistry.counter("users.registered").increment();
        log.info("User registered: userId={}", user.getId());

        return new LoginResult(jwtService.issueAccessToken(user.getId(), user.getRole()), buildCookie(rawToken));
    }

    @Transactional
    public LoginResult login(LoginRequest req) {
        User user = userRepo.findByEmail(req.email()).orElse(null);

        // Always run BCrypt to prevent timing-based account enumeration
        String hashToCheck = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(req.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            throw new UserServiceException(new UserError.InvalidCredentials());
        }
        if (!user.isActive()) {
            throw new UserServiceException(new UserError.UserDeactivated());
        }

        // Clean up expired refresh tokens for this user on each login
        refreshTokenRepo.deleteExpiredByUserId(user.getId(), Instant.now());

        String rawToken = generateRawToken();
        saveRefreshToken(user.getId(), rawToken);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        log.info("User logged in: userId={}", user.getId());
        return new LoginResult(accessToken, buildCookie(rawToken));
    }

    @Transactional
    public LoginResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UserServiceException(new UserError.InvalidRefreshToken());
        }

        String hashedToken = TokenHasher.hash(rawToken);
        RefreshToken stored = refreshTokenRepo.findByToken(hashedToken)
                .orElseThrow(() -> new UserServiceException(new UserError.InvalidRefreshToken()));

        if (stored.isRevoked()) {
            // Refresh token reuse detected — revoke ALL tokens for this user
            log.warn("Refresh token reuse detected: userId={}", stored.getUserId());
            refreshTokenRepo.revokeAllByUserId(stored.getUserId());
            throw new UserServiceException(new UserError.InvalidRefreshToken());
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new UserServiceException(new UserError.InvalidRefreshToken());
        }

        User user = userRepo.findById(stored.getUserId())
                .orElseThrow(() -> new UserServiceException(new UserError.InvalidRefreshToken()));

        if (!user.isActive()) {
            throw new UserServiceException(new UserError.UserDeactivated());
        }

        // Rotate: revoke old, issue new
        stored.setRevoked(true);
        refreshTokenRepo.save(stored);

        String newRawToken = generateRawToken();
        saveRefreshToken(user.getId(), newRawToken);

        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        return new LoginResult(accessToken, buildCookie(newRawToken));
    }

    @Transactional
    public ResponseCookie logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            String hashedToken = TokenHasher.hash(rawToken);
            refreshTokenRepo.findByToken(hashedToken).ifPresent(t -> {
                t.setRevoked(true);
                refreshTokenRepo.save(t);
            });
        }
        return clearCookie();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void saveRefreshToken(java.util.UUID userId, String rawToken) {
        var refreshToken = new RefreshToken();
        refreshToken.setToken(TokenHasher.hash(rawToken));
        refreshToken.setUserId(userId);
        refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(jwtProps.refreshExpiryDays())));
        refreshTokenRepo.save(refreshToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ResponseCookie buildCookie(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(Duration.ofDays(jwtProps.refreshExpiryDays()))
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    public record LoginResult(String accessToken, ResponseCookie cookie) {}
}
