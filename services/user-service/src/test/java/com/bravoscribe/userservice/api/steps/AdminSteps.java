package com.bravoscribe.userservice.api.steps;

import com.bravoscribe.userservice.entity.Role;
import com.bravoscribe.userservice.entity.User;
import com.bravoscribe.userservice.repository.UserRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminSteps {

    @Autowired StepContext ctx;
    @Autowired UserRepository userRepo;
    @Autowired PasswordEncoder passwordEncoder;

    @Given("an admin user with email {string} and password {string}")
    public void anAdminUser(String email, String password) {
        if (userRepo.findByEmail(email).isEmpty()) {
            User admin = new User();
            admin.setName("Admin User");
            admin.setEmail(email);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            userRepo.save(admin);
        }
    }

    @When("I get the user list")
    public void getUserList() {
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .get("");
        ctx.setLastResponse(r);
    }

    @When("I get the user list without authentication")
    public void getUserListWithoutAuth() {
        Response r = RestAssured.given().get("");
        ctx.setLastResponse(r);
    }

    @Then("the response contains a list of users")
    public void responseContainsListOfUsers() {
        assertThat(ctx.getLastResponse().jsonPath().getList("content")).isNotNull();
    }

    @When("I deactivate the saved user")
    public void deactivateSavedUser() {
        Response r = RestAssured.given()
                .header("Authorization", "Bearer " + ctx.getAccessToken())
                .put("/" + ctx.getTargetUserId() + "/deactivate");
        ctx.setLastResponse(r);
    }
}
