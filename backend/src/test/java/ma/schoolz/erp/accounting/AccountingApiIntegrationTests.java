package ma.schoolz.erp.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.LocalDate;
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
            "spring.datasource.url=jdbc:h2:mem:accounting;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "schoolz.demo.seed-enabled=true",
            "schoolz.demo.tenant-slug=ecole-demo",
            "schoolz.demo.tenant-name=École Démo",
            "schoolz.demo.admin-email=admin@schoolz.ma",
            "schoolz.demo.admin-password=SchoolZDemo2026!",
            "schoolz.demo.teacher-email=prof.math@schoolz.ma",
            "schoolz.demo.teacher-password=SchoolZTeacher2026!",
            "schoolz.demo.student-email=yasmine.eleve@schoolz.ma",
            "schoolz.demo.student-password=SchoolZStudent2026!",
            "schoolz.payments.sandbox.webhook-secret=schoolz-test-accounting-webhook-secret"
        })
class AccountingApiIntegrationTests {
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
    void dashboardExposesBalancedOptionalAccountingModule() throws Exception {
        mvc.perform(
                        get("/api/v1/accounting/dashboard")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.enabled").value(true))
                .andExpect(jsonPath("$.configuration.familyLedgerAutoPost").value(true))
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.totalDebit").value(org.hamcrest.Matchers.isA(Number.class)))
                .andExpect(jsonPath("$.accounts.length()").value(10))
                .andExpect(jsonPath("$.journals.length()").value(5))
                .andExpect(jsonPath("$.fiscalPeriods.length()").value(1))
                .andExpect(jsonPath("$.supplierInvoices[0].status").value("POSTED"))
                .andExpect(jsonPath("$.expenses[0].status").value("POSTED"))
                .andExpect(jsonPath("$.reconciliations[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.familyLedgerEntryCount").value(greaterThanOrEqualTo(2)));
    }

    @Test
    void everyPostedEntryAndLineRespectsDoubleEntryInvariants() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_entry"
                                        + " WHERE total_debit<=0 OR total_debit<>total_credit",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_line"
                                        + " WHERE NOT ((debit_amount>0 AND credit_amount=0)"
                                        + " OR (credit_amount>0 AND debit_amount=0))",
                                Integer.class))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_entry e WHERE"
                                    + " e.total_debit<>(SELECT COALESCE(SUM(l.debit_amount),0) FROM"
                                    + " accounting_journal_line l WHERE l.tenant_id=e.tenant_id AND"
                                    + " l.journal_entry_id=e.id) OR e.total_credit<>(SELECT"
                                    + " COALESCE(SUM(l.credit_amount),0) FROM"
                                    + " accounting_journal_line l WHERE l.tenant_id=e.tenant_id AND"
                                    + " l.journal_entry_id=e.id)",
                                Integer.class))
                .isZero();
    }

    @Test
    void supplierInvoiceCanOnlyPostThroughItsBusinessWorkflow() throws Exception {
        MvcResult dashboard =
                mvc.perform(
                                get("/api/v1/accounting/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        String body = dashboard.getResponse().getContentAsString();
        String supplierId =
                ((List<String>) JsonPath.read(body, "$.suppliers[?(@.code == 'FOUR-DEMO-01')].id"))
                        .getFirst();
        String journalId =
                ((List<String>) JsonPath.read(body, "$.journals[?(@.code == 'ACH')].id"))
                        .getFirst();
        String expenseAccountId =
                ((List<String>) JsonPath.read(body, "$.accounts[?(@.code == '614100')].id"))
                        .getFirst();
        String payableAccountId =
                ((List<String>) JsonPath.read(body, "$.accounts[?(@.code == '441100')].id"))
                        .getFirst();
        String costCenterId =
                ((List<String>) JsonPath.read(body, "$.costCenters[?(@.code == 'PRIMAIRE')].id"))
                        .getFirst();
        String invoiceNumber = "TEST-" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult created =
                mvc.perform(
                                post("/api/v1/accounting/supplier-invoices")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.ofEntries(
                                                                Map.entry("supplierId", supplierId),
                                                                Map.entry("journalId", journalId),
                                                                Map.entry(
                                                                        "expenseAccountId",
                                                                        expenseAccountId),
                                                                Map.entry(
                                                                        "payableAccountId",
                                                                        payableAccountId),
                                                                Map.entry(
                                                                        "costCenterId",
                                                                        costCenterId),
                                                                Map.entry(
                                                                        "invoiceNumber",
                                                                        invoiceNumber),
                                                                Map.entry(
                                                                        "invoiceDate",
                                                                        LocalDate.of(2026, 9, 10)),
                                                                Map.entry(
                                                                        "dueDate",
                                                                        LocalDate.of(2026, 10, 10)),
                                                                Map.entry(
                                                                        "description",
                                                                        "Fournitures de test"),
                                                                Map.entry(
                                                                        "totalAmount",
                                                                        new BigDecimal("775.25")),
                                                                Map.entry("currency", "MAD")))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andReturn();
        String invoiceId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/accounting/supplier-invoices/" + invoiceId + "/post")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.journalEntryId").isNotEmpty());
        mvc.perform(
                        post("/api/v1/accounting/supplier-invoices/" + invoiceId + "/post")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict());

        mvc.perform(
                        post("/api/v1/accounting/entries")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"lines\":[]}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void familyLedgerEventPostsExactlyOneBalancedEntryAndStudentIsForbidden() throws Exception {
        MvcResult finance =
                mvc.perform(
                                get("/api/v1/billing/dashboard")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                        .andReturn();
        String accountId =
                JsonPath.read(
                        finance.getResponse().getContentAsString(), "$.accounts[0].account.id");
        mvc.perform(
                        post("/api/v1/billing/accounts/" + accountId + "/adjustments")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "direction",
                                                        "DEBIT",
                                                        "amount",
                                                        new BigDecimal("17.35"),
                                                        "reason",
                                                        "Pont comptable M27"))))
                .andExpect(status().isCreated());
        String adjustmentId =
                jdbc.queryForObject(
                                "SELECT id FROM billing_financial_adjustment WHERE reason='Pont"
                                        + " comptable M27' ORDER BY posted_at DESC LIMIT 1",
                                UUID.class)
                        .toString();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_entry WHERE"
                                        + " source_module='FAMILY_LEDGER' AND"
                                        + " source_type='ADJUSTMENT_DEBIT' AND source_id=? AND"
                                        + " total_debit=total_credit",
                                Integer.class,
                                adjustmentId))
                .isEqualTo(1);
        mvc.perform(
                        post("/api/v1/accounting/family-ledger/sync")
                                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM accounting_journal_entry WHERE"
                                        + " source_module='FAMILY_LEDGER' AND"
                                        + " source_type='ADJUSTMENT_DEBIT' AND source_id=?",
                                Integer.class,
                                adjustmentId))
                .isEqualTo(1);

        mvc.perform(
                        get("/api/v1/accounting/dashboard")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(
                                                login(
                                                        "yasmine.eleve@schoolz.ma",
                                                        "SchoolZStudent2026!"))))
                .andExpect(status().isForbidden());
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
                        .andExpect(
                                jsonPath("$.user.roles")
                                        .value(hasItem(org.hamcrest.Matchers.anything())))
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
