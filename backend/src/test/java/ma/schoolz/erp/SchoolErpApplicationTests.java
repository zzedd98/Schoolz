package ma.schoolz.erp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchoolErpApplicationTests {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndFlywayAppliesBaseline() {
        String schemaOwner =
                jdbcTemplate.queryForObject(
                        "SELECT metadata_value FROM platform_metadata WHERE metadata_key = ?",
                        String.class,
                        "schema_owner");

        assertThat(schemaOwner).isEqualTo("schoolz-platform");
    }
}
