package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Direct JDBC access to the shared Postgres users schema — feature files reuse the
 *  same literal emails (e.g. "bruno@email.com") across scenarios, but containers/DB
 *  persist for the whole suite run (Lesson L5: delete-then-create avoids 409 conflicts
 *  from a prior scenario's leftover row). ON DELETE CASCADE on refresh_tokens and
 *  password_reset_tokens means deleting the user row is enough. */
final class UserDb {

    private UserDb() {}

    static void deleteUserByEmail(String email) {
        String url = "jdbc:postgresql://" + ServiceConfig.POSTGRES.getHost() + ":"
                + ServiceConfig.POSTGRES.getMappedPort(5432) + "/journal?currentSchema=users";
        try (Connection conn = DriverManager.getConnection(url, "user_svc", "user_svc_password");
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM users.users WHERE email = ?")) {
            stmt.setString(1, email);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
