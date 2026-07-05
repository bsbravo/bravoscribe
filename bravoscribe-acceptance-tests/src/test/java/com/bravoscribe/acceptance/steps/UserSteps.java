package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserSteps {

    private final TestContext ctx;

    public UserSteps(TestContext ctx) {
        this.ctx = ctx;
    }

    @When("I register with email {string} and password {string}")
    public void iRegisterWithEmailAndPassword(String email, String password) {
        ctx.currentEmail = email;
        ctx.currentPassword = password;
        ctx.lastResponse = UserApi.register("Test User", email, password);
        if (ctx.lastResponse.statusCode() == 201) {
            storeSession(ctx.lastResponse.jsonPath().getString("accessToken"));
        }
    }

    @Given("I am registered as {string}")
    public void iAmRegisteredAs(String email) {
        ctx.currentEmail = email;
        ctx.currentPassword = "P@ssword123";
        Response response = UserApi.register("Test User", email, ctx.currentPassword);
        storeSession(response.jsonPath().getString("accessToken"));
    }

    @Given("I am registered and logged in as {string}")
    public void iAmRegisteredAndLoggedInAs(String email) {
        iAmRegisteredAs(email);
        loginWithCurrentCredentials();
    }

    @Given("I am registered and logged in")
    public void iAmRegisteredAndLoggedIn() {
        iAmRegisteredAndLoggedInAs("acceptance-" + UUID.randomUUID() + "@email.com");
    }

    @Given("I am logged in and my access token has expired")
    public void iAmLoggedInAndMyAccessTokenHasExpired() {
        // ctx.userId is all CommonSteps needs to mint an already-expired token
        // for the actual request — see "I call GET ... with the expired token".
        iAmRegisteredAndLoggedIn();
    }

    @When("I login with those credentials")
    public void iLoginWithThoseCredentials() {
        loginWithCurrentCredentials();
    }

    @Then("I can login with the new password")
    public void iCanLoginWithTheNewPassword() {
        loginWithCurrentCredentials();
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(200);
    }

    @When("I login with the new password")
    public void iLoginWithTheNewPassword() {
        loginWithCurrentCredentials();
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(200);
    }

    @Then("the old password no longer works")
    public void theOldPasswordNoLongerWorks() {
        Response response = UserApi.login(ctx.currentEmail, ctx.previousPassword);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @When("I request a password reset for {string}")
    public void iRequestAPasswordResetFor(String email) {
        ctx.lastResponse = RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of("email", email))
                .post("/password-reset/request");
    }

    @When("I reset my password via the reset flow")
    public void iResetMyPasswordViaTheResetFlow() {
        iRequestAPasswordResetFor(ctx.currentEmail);
        String body = WireMockApi.awaitEmailBodyContaining(ctx.currentEmail);
        String token = WireMockApi.extractResetToken(body);
        confirmReset(token, "NewP@ssword456");
    }

    @When("I confirm the reset with a valid token and new password {string}")
    public void iConfirmTheResetWithAValidTokenAndNewPassword(String newPassword) {
        confirmReset(ctx.resetToken, newPassword);
    }

    private void confirmReset(String token, String newPassword) {
        Response response = RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .contentType(ContentType.JSON)
                .body(Map.of("token", token, "newPassword", newPassword))
                .put("/password-reset/confirm");
        ctx.lastResponse = response;
        ctx.previousPassword = ctx.currentPassword;
        ctx.currentPassword = newPassword;
    }

    @When("an admin deactivates my account")
    public void anAdminDeactivatesMyAccount() {
        deactivateCurrentUser();
    }

    @When("an admin deactivates my account via the User Service API")
    public void anAdminDeactivatesMyAccountViaTheUserServiceApi() {
        deactivateCurrentUser();
    }

    private void deactivateCurrentUser() {
        String adminToken = ServiceConfig.mintAdminAccessToken();
        ctx.lastResponse = RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .header("Authorization", "Bearer " + adminToken)
                .put("/" + ctx.userId + "/deactivate");
    }

    @Then("I cannot login anymore")
    public void iCannotLoginAnymore() {
        assertLoginFails();
    }

    @Then("I cannot login with my credentials anymore")
    public void iCannotLoginWithMyCredentialsAnymore() {
        assertLoginFails();
    }

    @Then("login returns 401")
    public void loginReturns401() {
        assertLoginFails();
    }

    private void assertLoginFails() {
        Response response = UserApi.login(ctx.currentEmail, ctx.currentPassword);
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // KNOWN, ACCEPTED GAP (not a silently-dropped regression guard): access tokens
    // are stateless and short-lived — deactivation revokes refresh tokens, not an
    // already-issued access token, so a deactivated user's token stays valid for up
    // to JWT_ACCESS_EXPIRY_SECONDS (900s default) after deactivation. This scenario
    // originally asserted immediate cross-service revocation; it was deliberately
    // rewritten to assert current actual behavior after discussing the tradeoff
    // (real-time revocation via a Redis deny-list vs. accepting this window) — see
    // bravoscribe-acceptance-tests/PLAN.md "Known accepted gaps".
    @Then("my old access token remains valid until it expires")
    public void myOldAccessTokenRemainsValidUntilItExpires() {
        Response response = RestAssured.given()
                .baseUri(ServiceConfig.journalServiceBaseUrl())
                .header("Authorization", "Bearer " + ctx.accessToken)
                .get("/entries");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Then("I cannot obtain a new access token via \\/refresh")
    public void iCannotObtainANewAccessTokenViaRefresh() {
        Response response = RestAssured.given()
                .baseUri(ServiceConfig.userServiceBaseUrl())
                .cookie("refreshToken", ctx.refreshTokenCookie)
                .post("/refresh");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Given("user A is registered and has a journal entry")
    public void userAIsRegisteredAndHasAJournalEntry() {
        UserApi.registerAndLogin(ctx.named("A"), "userA");
        Response response = JournalApi.createEntry(ctx.named("A").accessToken, LocalDate.now(), "Entry by user A");
        ctx.named("A").entryId = UUID.fromString(response.jsonPath().getString("id"));
    }

    @Given("user B is registered and logged in")
    public void userBIsRegisteredAndLoggedIn() {
        UserApi.registerAndLogin(ctx.named("B"), "userB");
    }

    @When("user B calls GET \\/api\\/journal\\/entries for user A's entry ID")
    public void userBCallsGetApiJournalEntriesForUserAsEntryId() {
        ctx.lastResponse = JournalApi.getEntry(ctx.named("B").accessToken, ctx.named("A").entryId);
    }

    @Then("user B receives status 404")
    public void userBReceivesStatus404() {
        assertThat(ctx.lastResponse.statusCode()).isEqualTo(404);
    }

    private void loginWithCurrentCredentials() {
        Response response = UserApi.login(ctx.currentEmail, ctx.currentPassword);
        ctx.lastResponse = response;
        if (response.statusCode() == 200) {
            storeSession(response.jsonPath().getString("accessToken"));
            ctx.refreshTokenCookie = response.getCookie("refreshToken");
        }
    }

    private void storeSession(String accessToken) {
        ctx.accessToken = accessToken;
        ctx.userId = UserApi.extractUserId(accessToken);
    }
}
