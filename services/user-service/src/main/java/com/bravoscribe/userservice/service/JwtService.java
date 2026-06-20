package com.bravoscribe.userservice.service;

import com.bravoscribe.userservice.config.JwtProperties;
import com.bravoscribe.userservice.entity.Role;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties props) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
    }

    public String issueAccessToken(UUID userId, Role role) {
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .claim("role", role.name())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(props.accessExpirySeconds()))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
