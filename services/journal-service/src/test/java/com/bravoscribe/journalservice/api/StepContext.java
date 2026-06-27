package com.bravoscribe.journalservice.api;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ScenarioScope
public class StepContext {

    private Response lastResponse;
    private String accessToken;
    private String otherUserToken;
    private UUID lastEntryId;
    private UUID lastTagId;
    private UUID otherUserEntryId;

    public void reset() {
        lastResponse = null;
        lastEntryId = null;
        lastTagId = null;
        otherUserEntryId = null;
    }

    public Response getLastResponse() { return lastResponse; }
    public void setLastResponse(Response r) { this.lastResponse = r; }

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String t) { this.accessToken = t; }

    public String getOtherUserToken() { return otherUserToken; }
    public void setOtherUserToken(String t) { this.otherUserToken = t; }

    public UUID getLastEntryId() { return lastEntryId; }
    public void setLastEntryId(UUID id) { this.lastEntryId = id; }

    public UUID getLastTagId() { return lastTagId; }
    public void setLastTagId(UUID id) { this.lastTagId = id; }

    public UUID getOtherUserEntryId() { return otherUserEntryId; }
    public void setOtherUserEntryId(UUID id) { this.otherUserEntryId = id; }
}
