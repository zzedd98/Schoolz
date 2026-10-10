package ma.schoolz.erp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class SchoolScalePerformanceIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine")
                    .withDatabaseName("school_erp_scale")
                    .withUsername("school_erp")
                    .withPassword("school_erp_scale");

    @Test
    void directoryRemainsBoundedWithTwentyThousandStudents() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (var connection =
                DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(
                        "INSERT INTO identity_tenant"
                                + " (id,slug,display_name,active,created_at) VALUES"
                                + " ('11111111-1111-1111-1111-111111111111','scale-school','École"
                                + " échelle',TRUE,CURRENT_TIMESTAMP)");
                statement.executeUpdate(
                        "INSERT INTO person"
                            + " (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)"
                            + " SELECT md5('person-' || g)::uuid,"
                            + " '11111111-1111-1111-1111-111111111111','Élève ' || g, 'Famille ' ||"
                            + " lpad(g::text,5,'0'),DATE '2014-01-01' + (g % 365),"
                            + " 'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM"
                            + " generate_series(1,20000) g");
                statement.executeUpdate(
                        "INSERT INTO student_profile"
                            + " (id,tenant_id,person_id,status,created_at,updated_at) SELECT"
                            + " md5('student-' || g)::uuid,"
                            + " '11111111-1111-1111-1111-111111111111',md5('person-' || g)::uuid,"
                            + " 'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM"
                            + " generate_series(1,20000) g");
                statement.executeUpdate(
                        "INSERT INTO student_identifier"
                            + " (id,tenant_id,student_id,identifier_type,identifier_value,issuer,primary_identifier,created_at)"
                            + " SELECT md5('identifier-' || g)::uuid,"
                            + " '11111111-1111-1111-1111-111111111111',md5('student-' || g)::uuid,"
                            + " 'SCHOOL','PERF-' ||"
                            + " lpad(g::text,5,'0'),'SchoolZ',TRUE,CURRENT_TIMESTAMP FROM"
                            + " generate_series(1,20000) g");
                statement.execute("ANALYZE person");
                statement.execute("ANALYZE student_profile");
                statement.execute("ANALYZE student_identifier");
            }

            Instant started = Instant.now();
            int rows = 0;
            try (var statement =
                    connection.prepareStatement(
                            "SELECT s.id,p.first_name,p.last_name,i.identifier_value FROM"
                                    + " student_profile s JOIN person p ON p.id=s.person_id AND"
                                    + " p.tenant_id=s.tenant_id LEFT JOIN student_identifier i ON"
                                    + " i.student_id=s.id AND i.tenant_id=s.tenant_id AND"
                                    + " i.primary_identifier=TRUE WHERE s.tenant_id=? ORDER BY"
                                    + " p.last_name,p.first_name,s.id LIMIT ? OFFSET ?")) {
                statement.setObject(
                        1, java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
                statement.setInt(2, 41);
                statement.setInt(3, 0);
                try (var result = statement.executeQuery()) {
                    while (result.next()) rows++;
                }
            }
            Duration duration = Duration.between(started, Instant.now());
            assertThat(rows).isEqualTo(41);
            assertThat(duration).isLessThan(Duration.ofSeconds(5));

            List<String> indexes = new ArrayList<>();
            try (var statement =
                            connection.prepareStatement(
                                    "SELECT indexname FROM pg_indexes WHERE schemaname='public' AND"
                                            + " indexname IN"
                                            + " ('idx_student_directory_cover',"
                                            + " 'idx_student_identifier_primary',"
                                            + " 'idx_student_enrollment_active',"
                                            + " 'idx_person_directory_order')");
                    var result = statement.executeQuery()) {
                while (result.next()) indexes.add(result.getString(1));
            }
            assertThat(indexes).hasSize(4);
        }
    }
}
