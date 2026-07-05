package com.bravoscribe.acceptance.steps;

import com.bravoscribe.acceptance.config.ServiceConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Direct JDBC access to the shared Postgres journal schema — for asserting DB state
 *  that isn't observable through journal-service's own HTTP API (e.g. soft-delete flags
 *  after an asynchronous Kafka-driven deactivation). */
final class JournalDb {

    private JournalDb() {}

    static List<Boolean> deletedFlagsForUser(UUID userId) {
        List<Boolean> flags = new ArrayList<>();
        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT deleted FROM journal.journal_entries WHERE user_id = ?")) {
            stmt.setObject(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    flags.add(rs.getBoolean("deleted"));
                }
            }
            return flags;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(ServiceConfig.postgresJdbcUrl(), "journal_svc", "journal_svc_password");
    }
}
