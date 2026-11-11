package com.example.parkngo.config;

import java.sql.*;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in SQL Server test. All test schema and data changes are rolled back. */
@EnabledIfEnvironmentVariable(named="PARKNGO_MIGRATION_DB_URL", matches=".+")
class MembershipBenefitMigrationTest {
    @Test void longIdsForeignKeysAndRepeatedStartup() throws Exception {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(System.getenv("PARKNGO_MIGRATION_DB_URL"),
                System.getenv("PARKNGO_MIGRATION_DB_USERNAME"), System.getenv("PARKNGO_MIGRATION_DB_PASSWORD"))) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                s.execute("CREATE SCHEMA " + schema);
                for (String[] p : new String[][] {{"Package","PackageID"},{"FacilityService","ServiceID"},{"Subscription","SubscriptionID"}}) {
                    s.execute("CREATE TABLE " + schema + ".[" + p[0] + "] (" + p[1] + " BIGINT PRIMARY KEY)");
                    s.execute("INSERT INTO " + schema + ".[" + p[0] + "] VALUES (2147483648)");
                }
                String sql;
                try (var stream = new ClassPathResource("db/migration/V20261008_03__membership_benefits.sql").getInputStream()) {
                    sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("dbo.", schema + ".");
                }
                var migration = new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8));
                ScriptUtils.executeSqlScript(c, migration);
                ScriptUtils.executeSqlScript(c, migration);
                try (ResultSet r = s.executeQuery("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='" + schema
                        + "' AND TABLE_NAME IN ('MembershipBenefit','MembershipBenefitUsage') AND COLUMN_NAME IN ('BenefitID','PackageID','ServiceID','UsageID','SubscriptionID')")) {
                    int count=0;
                    while(r.next()) { assertEquals("bigint", r.getString(1)); count++; }
                    assertEquals(6,count);
                }
                s.execute("SET IDENTITY_INSERT " + schema + ".MembershipBenefit ON");
                s.execute("INSERT INTO " + schema + ".MembershipBenefit (BenefitID,PackageID,BenefitType,ServiceID) VALUES (2147483648,2147483648,'PARKING_DISCOUNT',2147483648)");
                s.execute("SET IDENTITY_INSERT " + schema + ".MembershipBenefit OFF");
                s.execute("INSERT INTO " + schema + ".MembershipBenefitUsage (SubscriptionID,BenefitID,PeriodStart) VALUES (2147483648,2147483648,'2026-10-16')");
                assertThrows(SQLException.class, () -> s.execute("INSERT INTO " + schema + ".MembershipBenefit (PackageID,BenefitType) VALUES (999,'PARKING_DISCOUNT')"));
            } finally { c.rollback(); }
        }
    }
}
