package com.bravoscribe.userservice.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private final JwtProperties props;

    public JwtConfig(JwtProperties props) {
        this.props = props;
    }

    @Bean
    public RSAPublicKey rsaPublicKey() {
        try {
            return parsePublicKey(props.publicKey());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JWT_PUBLIC_KEY", e);
        }
    }

    @Bean
    public RSAPrivateKey rsaPrivateKey() {
        try {
            return parsePrivateKey(props.privateKey());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse JWT_PRIVATE_KEY", e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAPublicKey rsaPublicKey, RSAPrivateKey rsaPrivateKey) {
        var rsaKey = new RSAKey.Builder(rsaPublicKey).privateKey(rsaPrivateKey).build();
        var jwkSet = new JWKSet(rsaKey);
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(jwkSet));
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAPublicKey rsaPublicKey) {
        return NimbusJwtDecoder.withPublicKey(rsaPublicKey)
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String cleaned = stripPem(pem, "PUBLIC KEY");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(decoded));
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String cleaned = stripPem(pem, "PRIVATE KEY");
        byte[] decoded = Base64.getDecoder().decode(cleaned);
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    // Handles both real newlines and literal \n from env var single-line PEM
    private String stripPem(String pem, String label) {
        return pem
                .replace("\\n", "\n")
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s+", "");
    }
}
