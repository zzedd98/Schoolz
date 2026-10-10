package ma.schoolz.erp.procurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
            "spring.datasource.url=jdbc:h2:mem:procurement;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-procurement-webhook-secret"
        })
class ProcurementApiIntegrationTests {
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
    void dashboardExposesTheCompleteControlledPurchaseCycle() throws Exception {
        mvc.perform(
                        get("/api/v1/procurement/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.baseCurrency").value("MAD"))
                .andExpect(jsonPath("$.configuration.allowPartialReceipts").value(true))
                .andExpect(jsonPath("$.thresholds.length()").value(3))
                .andExpect(jsonPath("$.requests.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.orders.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.receipts.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.invoiceMatches.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.payments.length()").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.statistics.awaitingApproval").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.statistics.paidAmount").value(greaterThanOrEqualTo(12600.0)))
                .andExpect(jsonPath("$.accounting.suppliers[0].code").value("FOUR-DEMO-01"));
    }

    @Test
    void seededPaymentIsBalancedAndReusesTheAccountingSupplierMaster() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_supplier WHERE"
                                        + " code='FOUR-DEMO-01'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM procurement_purchase_request WHERE"
                                        + " status='PAID'",
                                Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_entry WHERE"
                                    + " source_module='PROCUREMENT' AND total_debit=total_credit"
                                    + " AND total_debit>0",
                                Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM procurement_supplier_payment p JOIN"
                                    + " accounting_journal_entry e ON e.id=p.journal_entry_id AND"
                                    + " e.tenant_id=p.tenant_id WHERE p.amount=e.total_debit",
                                Integer.class))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void invoiceIsRejectedUntilTheApprovedOrderIsFullyReceived() throws Exception {
        String dashboard = dashboard();
        String costCenterId =
                first(dashboard, "$.accounting.costCenters[?(@.code == 'PRIMAIRE')].id");
        String supplierId =
                first(dashboard, "$.accounting.suppliers[?(@.code == 'FOUR-DEMO-01')].id");
        String journalId = first(dashboard, "$.accounting.journals[?(@.code == 'ACH')].id");
        String expenseAccountId =
                first(dashboard, "$.accounting.accounts[?(@.code == '614100')].id");
        String payableAccountId =
                first(dashboard, "$.accounting.accounts[?(@.code == '441100')].id");

        MvcResult created =
                mvc.perform(
                                post("/api/v1/procurement/requests")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "costCenterId",
                                                                costCenterId,
                                                                "requestedOn",
                                                                "2026-08-10",
                                                                "title",
                                                                "Demande de contrôle "
                                                                        + UUID.randomUUID(),
                                                                "businessNeed",
                                                                "Valider l’ordre strict du"
                                                                        + " workflow.",
                                                                "urgency",
                                                                "NORMAL",
                                                                "lines",
                                                                List.of(
                                                                        Map.of(
                                                                                "description",
                                                                                "Matériel de test",
                                                                                "quantity",
                                                                                2,
                                                                                "unit",
                                                                                "unité",
                                                                                "estimatedUnitPrice",
                                                                                400))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.estimatedAmount").value(800.0))
                        .andReturn();
        String requestId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/procurement/requests/" + requestId + "/submit")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/api/v1/procurement/requests/" + requestId + "/decision")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("decision", "APPROVED", "note", "OK"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        MvcResult ordered =
                mvc.perform(
                                post("/api/v1/procurement/requests/" + requestId + "/orders")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "supplierId",
                                                                supplierId,
                                                                "orderedOn",
                                                                "2026-08-11",
                                                                "expectedOn",
                                                                "2026-08-20"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("ISSUED"))
                        .andReturn();
        String orderId = JsonPath.read(ordered.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/procurement/orders/" + orderId + "/invoice")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.ofEntries(
                                                        Map.entry("journalId", journalId),
                                                        Map.entry(
                                                                "expenseAccountId",
                                                                expenseAccountId),
                                                        Map.entry(
                                                                "payableAccountId",
                                                                payableAccountId),
                                                        Map.entry("costCenterId", costCenterId),
                                                        Map.entry(
                                                                "invoiceNumber",
                                                                "PREMATURE-" + UUID.randomUUID()),
                                                        Map.entry("invoiceDate", "2026-08-12"),
                                                        Map.entry("dueDate", "2026-09-12"),
                                                        Map.entry(
                                                                "description",
                                                                "Facture prématurée"),
                                                        Map.entry("totalAmount", 800)))))
                .andExpect(status().isConflict());

        String orderLineId =
                JsonPath.read(ordered.getResponse().getContentAsString(), "$.lines[0].id");
        mvc.perform(
                        post("/api/v1/procurement/orders/" + orderId + "/receipts")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "receivedOn",
                                                        "2026-08-13",
                                                        "lines",
                                                        List.of(
                                                                Map.of(
                                                                        "orderLineId",
                                                                        orderLineId,
                                                                        "quantityReceived",
                                                                        3))))))
                .andExpect(status().isConflict());
    }

    @Test
    void strategicThresholdRequiresDistinctApprovalsAndStudentIsForbidden() throws Exception {
        String strategicId =
                jdbc.queryForObject(
                                "SELECT id FROM procurement_purchase_request WHERE"
                                        + " estimated_amount=38000.00",
                                UUID.class)
                        .toString();
        mvc.perform(
                        post("/api/v1/procurement/requests/" + strategicId + "/decision")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsString(Map.of("decision", "APPROVED"))))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM procurement_purchase_request WHERE id=?",
                                String.class,
                                UUID.fromString(strategicId)))
                .isEqualTo("SUBMITTED");

        mvc.perform(
                        get("/api/v1/procurement/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
    }

    private String dashboard() throws Exception {
        return mvc.perform(
                        get("/api/v1/procurement/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private String first(String body, String path) {
        return ((List<String>) JsonPath.read(body, path)).getFirst();
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
