package com.bravoscribe.notificationservice.config;

import com.bravoscribe.notificationservice.service.SendGridGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Configuration
public class SendGridConfig {

    @Value("${notification.sendgrid.api-key}")
    private String apiKey;

    @Value("${notification.from-email}")
    private String fromEmail;

    @Value("${notification.sendgrid.base-url:https://api.sendgrid.com}")
    private String baseUrl;

    @Bean
    public RestClient sendGridClient() {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
    }

    @Bean
    public SendGridGateway sendGridGateway(RestClient sendGridClient) {
        return (to, subject, body) -> {
            var payload = Map.of(
                "personalizations", List.of(Map.of("to", List.of(Map.of("email", to)))),
                "from", Map.of("email", fromEmail),
                "subject", subject,
                "content", List.of(Map.of("type", "text/plain", "value", body))
            );
            var response = sendGridClient.post()
                .uri("/v3/mail/send")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            return response.getHeaders().getFirst("X-Message-Id");
        };
    }
}
