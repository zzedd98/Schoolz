package ma.schoolz.erp.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("dev")
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:inventory;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-inventory-webhook-secret"
        })
class InventoryApiIntegrationTests {
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminToken = login("admin@schoolz.ma", "SchoolZDemo2026!");
    }

    @Test
    void dashboardExposesAssetsStocksLocationsAndCompleteHistory() throws Exception {
        mvc.perform(
                        get("/api/v1/inventory/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locations.length()").value(4))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.assets.length()").value(3))
                .andExpect(jsonPath("$.statistics.assignedAssets").value(1))
                .andExpect(jsonPath("$.statistics.maintenanceAssets").value(1))
                .andExpect(jsonPath("$.statistics.lowStockItems").value(1))
                .andExpect(jsonPath("$.movements.length()").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.assignments.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.assetHistory.length()").value(greaterThanOrEqualTo(7)));
    }

    @Test
    void stockCannotBecomeNegativeAndTransferPreservesTheTotal() throws Exception {
        UUID itemId =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_item WHERE sku='MAT-CREA-001'", UUID.class);
        UUID sourceId =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_location WHERE code='PRIM-1AP-A'", UUID.class);
        UUID destinationId =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_location WHERE code='MAG-CENTRAL'", UUID.class);
        BigDecimal before = total(itemId);

        mvc.perform(
                        post("/api/v1/inventory/items/" + itemId + "/movements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "movementType",
                                                        "OUTBOUND",
                                                        "fromLocationId",
                                                        sourceId,
                                                        "quantity",
                                                        13,
                                                        "occurredOn",
                                                        "2026-09-05",
                                                        "reason",
                                                        "Sortie supérieure au disponible"))))
                .andExpect(status().isConflict());

        mvc.perform(
                        post("/api/v1/inventory/items/" + itemId + "/movements")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "movementType",
                                                        "TRANSFER",
                                                        "fromLocationId",
                                                        sourceId,
                                                        "toLocationId",
                                                        destinationId,
                                                        "quantity",
                                                        2,
                                                        "occurredOn",
                                                        "2026-09-05",
                                                        "reference",
                                                        "TEST-TRANSFERT",
                                                        "reason",
                                                        "Retour partiel en réserve"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movementType").value("TRANSFER"));

        assertThat(total(itemId)).isEqualByComparingTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT quantity FROM inventory_stock_balance WHERE"
                                        + " inventory_item_id=? AND location_id=?",
                                BigDecimal.class,
                                itemId,
                                sourceId))
                .isEqualByComparingTo("10.000");
    }

    @Test
    void anAssetCanBeAssignedReturnedAndKeepsEveryEvent() throws Exception {
        UUID locationId =
                jdbc.queryForObject(
                        "SELECT id FROM inventory_location WHERE code='LAB-NUM'", UUID.class);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        MvcResult created =
                mvc.perform(
                                post("/api/v1/inventory/assets")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.ofEntries(
                                                                Map.entry("locationId", locationId),
                                                                Map.entry(
                                                                        "assetTag",
                                                                        "SZ-TEST-" + suffix),
                                                                Map.entry(
                                                                        "name",
                                                                        "Tablette de recette"),
                                                                Map.entry("category", "TABLET"),
                                                                Map.entry("brand", "Demo"),
                                                                Map.entry("model", "QA"),
                                                                Map.entry(
                                                                        "serialNumber",
                                                                        "SER-" + suffix),
                                                                Map.entry(
                                                                        "acquiredOn", "2026-09-01"),
                                                                Map.entry("acquisitionValue", 1500),
                                                                Map.entry("currency", "MAD"),
                                                                Map.entry("condition", "NEW")))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("AVAILABLE"))
                        .andReturn();
        String assetId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        MvcResult assigned =
                mvc.perform(
                                post("/api/v1/inventory/assets/" + assetId + "/assignments")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "destinationLocationId",
                                                                locationId,
                                                                "assigneeType",
                                                                "PERSON",
                                                                "assigneeReference",
                                                                "recette@schoolz.ma",
                                                                "assigneeLabel",
                                                                "Utilisateur de recette",
                                                                "assignedOn",
                                                                "2026-09-02",
                                                                "dueOn",
                                                                "2026-09-10",
                                                                "conditionOut",
                                                                "NEW"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("ACTIVE"))
                        .andReturn();
        String assignmentId = JsonPath.read(assigned.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/inventory/assignments/" + assignmentId + "/return")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "returnLocationId",
                                                        locationId,
                                                        "returnedOn",
                                                        "2026-09-06",
                                                        "conditionIn",
                                                        "GOOD",
                                                        "note",
                                                        "Retour contrôlé"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_asset_event WHERE asset_id=?",
                                Integer.class,
                                UUID.fromString(assetId)))
                .isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM inventory_asset WHERE id=?",
                                String.class,
                                UUID.fromString(assetId)))
                .isEqualTo("AVAILABLE");
    }

    @Test
    void studentCannotOpenTheInternalInventory() throws Exception {
        mvc.perform(
                        get("/api/v1/inventory/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private BigDecimal total(UUID itemId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(sum(quantity),0) FROM inventory_stock_balance WHERE"
                        + " inventory_item_id=?",
                BigDecimal.class,
                itemId);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                "ecole-demo",
                                                                "email",
                                                                email,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
