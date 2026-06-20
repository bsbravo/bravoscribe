package com.bravoscribe.userservice.api.steps;

import io.restassured.response.Response;
import org.springframework.stereotype.Component;

/**
 * Shared state across Cucumber step definition classes within one scenario.
 * Cucumber Spring injects this as a singleton per scenario.
 */
@Component
public class StepContext {

    Response lastResponse;
    String accessToken;
    String refreshTokenCookie;
    String savedRefreshTokenCookie;
    String targetUserId;

    public void setLastResponse(Response response) {
        this.lastResponse = response;
        String cookieValue = response.getCookie("refreshToken");
        if (cookieValue != null) {
            this.refreshTokenCookie = cookieValue;
        }
        String contentType = response.contentType();
        if (contentType != null && contentType.contains("application/json")) {
            String token = response.jsonPath().getString("accessToken");
            if (token != null) {
                this.accessToken = token;
            }
        }
    }

    public Response getLastResponse() { return lastResponse; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshTokenCookie() { return refreshTokenCookie; }
    public String getSavedRefreshTokenCookie() { return savedRefreshTokenCookie; }
    public void setSavedRefreshTokenCookie(String v) { this.savedRefreshTokenCookie = v; }
    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String v) { this.targetUserId = v; }

    public void reset() {
        lastResponse = null;
        accessToken = null;
        refreshTokenCookie = null;
        savedRefreshTokenCookie = null;
        targetUserId = null;
    }
}
