package ma.schoolz.erp.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(
        properties =
                "schoolz.payments.sandbox.webhook-secret=schoolz-test-sandbox-webhook-secret-2026")
class BillingApiIntegrationTests {
    private static final String PASSWORD = "SchoolZFinance2026!";
    private static final String PARENT_PASSWORD = "SchoolZParent2026!";
    private static final String SANDBOX_SECRET = "schoolz-test-sandbox-webhook-secret-2026";

    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdentityTenantRepository tenants;
    @Autowired RoleRepository roles;
    @Autowired UserAccountRepository users;

    private MockMvc mvc;
    private UUID tenantId;
    private UUID portalAccountId;
    private UUID studentId;
    private String token;
    private Cookie parentCookie;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        cleanDatabase();
        IdentityTenant tenant =
                tenants.save(IdentityTenant.create("finance-a", "Finance A", clock.instant()));
        tenantId = tenant.id();
        Role role =
                roles.save(
                        Role.create(
                                tenantId,
                                "FINANCE_TEST",
                                "Finance test",
                                false,
                                Set.of(
                                        "invoice.read",
                                        "invoice.create",
                                        "invoice.adjust",
                                        "payment.record",
                                        "payment.reverse",
                                        "collections.read",
                                        "collections.manage",
                                        "online-payment.read",
                                        "online-payment.manage",
                                        "online-payment.refund"),
                                clock.instant()));
        UserAccount accountant =
                UserAccount.create(
                        tenantId,
                        "finance@finance-a.ma",
                        "Comptable Test",
                        passwordEncoder.encode(PASSWORD),
                        "fr-MA",
                        true,
                        clock.instant());
        accountant.assignRoles(Set.of(role), clock.instant());
        users.save(accountant);
        token = login("finance-a", "finance@finance-a.ma", PASSWORD);
        parentCookie = registerParent("finance-a", "parent@finance-a.ma");
        portalAccountId =
                jdbc.queryForObject(
                        "SELECT id FROM admission_portal_account WHERE tenant_id=?"
                                + " AND normalized_email='parent@finance-a.ma'",
                        UUID.class,
                        tenantId);
        studentId = createStudent(tenantId, "Yasmine", "Alaoui");
        jdbc.update(
                "INSERT INTO enrollment_portal_student_link"
                        + " (id,tenant_id,portal_account_id,student_id,created_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                tenantId,
                portalAccountId,
                studentId,
                timestamp(clock.instant()));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void paymentCanCoverSeveralInvoicesAndInvoiceCanReceiveSeveralPayments() throws Exception {
        String accountId = createFamilyAccount();
        String firstInvoice = createAndIssueInvoice(accountId, "Scolarité", "1000.00", "100.00");
        String secondInvoice = createAndIssueInvoice(accountId, "Transport", "800.00", "0.00");

        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/payments", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "1200.00",
                                                        "paymentMethod",
                                                        "bank_transfer",
                                                        "externalReference",
                                                        "VIR-2026-0001",
                                                        "allocations",
                                                        List.of(
                                                                Map.of(
                                                                        "invoiceId",
                                                                        firstInvoice,
                                                                        "amount",
                                                                        "700.00"),
                                                                Map.of(
                                                                        "invoiceId",
                                                                        secondInvoice,
                                                                        "amount",
                                                                        "500.00"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receiptNumber").isNotEmpty())
                .andExpect(jsonPath("$.allocations.length()").value(2));
        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/payments", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "200.00",
                                                        "paymentMethod",
                                                        "cash",
                                                        "externalReference",
                                                        "ESP-2026-0002",
                                                        "allocations",
                                                        List.of(
                                                                Map.of(
                                                                        "invoiceId",
                                                                        firstInvoice,
                                                                        "amount",
                                                                        "200.00"))))))
                .andExpect(status().isCreated());

        mvc.perform(
                        get("/api/v1/billing/accounts/{accountId}", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalBilled").value(1700.0))
                .andExpect(jsonPath("$.summary.totalPaid").value(1400.0))
                .andExpect(jsonPath("$.summary.balance").value(300.0))
                .andExpect(
                        jsonPath("$.invoices[?(@.id == '" + firstInvoice + "')].status")
                                .value("PAID"))
                .andExpect(
                        jsonPath("$.invoices[?(@.id == '" + secondInvoice + "')].balanceAmount")
                                .value(300.0));
        mvc.perform(get("/api/v1/public/finance").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.email").value("parent@finance-a.ma"))
                .andExpect(jsonPath("$.receipts.length()").value(2))
                .andExpect(jsonPath("$.summary.balance").value(300.0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(DISTINCT receipt_number) FROM billing_receipt"
                                        + " WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isEqualTo(2);
    }

    @Test
    void validatedPaymentIsReversedByCompensatingEntriesAndNeverDeleted() throws Exception {
        String accountId = createFamilyAccount();
        String invoiceId = createAndIssueInvoice(accountId, "Scolarité", "1000.00", "0.00");
        MvcResult payment =
                mvc.perform(
                                post("/api/v1/billing/accounts/{accountId}/payments", accountId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "amount",
                                                                "400.00",
                                                                "paymentMethod",
                                                                "card",
                                                                "externalReference",
                                                                "CB-2026-0001",
                                                                "allocations",
                                                                List.of(
                                                                        Map.of(
                                                                                "invoiceId",
                                                                                invoiceId,
                                                                                "amount",
                                                                                "400.00"))))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String paymentId = JsonPath.read(payment.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/billing/payments/{paymentId}/reverse", paymentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of("reason", "Erreur de saisie"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentType").value("REVERSAL"))
                .andExpect(jsonPath("$.reversalOfPaymentId").value(paymentId));

        mvc.perform(
                        get("/api/v1/billing/accounts/{accountId}", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalPaid").value(0.0))
                .andExpect(jsonPath("$.summary.balance").value(1000.0))
                .andExpect(jsonPath("$.payments.length()").value(2))
                .andExpect(jsonPath("$.receipts.length()").value(2))
                .andExpect(jsonPath("$.invoices[0].balanceAmount").value(1000.0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_payment WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=?"
                                        + " AND action='billing.payment.reversed'",
                                Integer.class,
                                tenantId))
                .isEqualTo(1);
    }

    @Test
    void installmentPlansDriveAgingAndScheduleAllocationsReverseWithThePayment() throws Exception {
        String accountId = createFamilyAccount();
        MvcResult plan =
                mvc.perform(
                                post(
                                                "/api/v1/billing/collections/accounts/{accountId}/installment-plans",
                                                accountId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Plan mensuel",
                                                                "frequency",
                                                                "monthly",
                                                                "totalAmount",
                                                                "12000.00",
                                                                "startsOn",
                                                                "2026-05-05",
                                                                "installmentCount",
                                                                3))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.frequency").value("MONTHLY"))
                        .andExpect(jsonPath("$.schedules.length()").value(3))
                        .andExpect(jsonPath("$.schedules[0].amount").value(4000.0))
                        .andExpect(jsonPath("$.schedules[2].dueDate").value("2026-07-05"))
                        .andReturn();
        String firstSchedule =
                JsonPath.read(plan.getResponse().getContentAsString(), "$.schedules[0].id");
        String secondSchedule =
                JsonPath.read(plan.getResponse().getContentAsString(), "$.schedules[1].id");

        MvcResult payment =
                mvc.perform(
                                post("/api/v1/billing/accounts/{accountId}/payments", accountId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "amount",
                                                                "8000.00",
                                                                "paymentMethod",
                                                                "bank_transfer",
                                                                "allocations",
                                                                List.of(),
                                                                "scheduleAllocations",
                                                                List.of(
                                                                        Map.of(
                                                                                "paymentScheduleId",
                                                                                firstSchedule,
                                                                                "amount",
                                                                                "4000.00"),
                                                                        Map.of(
                                                                                "paymentScheduleId",
                                                                                secondSchedule,
                                                                                "amount",
                                                                                "4000.00"))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.scheduleAllocations.length()").value(2))
                        .andReturn();
        String paymentId = JsonPath.read(payment.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        get("/api/v1/billing/collections/dashboard")
                                .queryParam("asOf", "2026-08-09")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpected").value(12000.0))
                .andExpect(jsonPath("$.totalReceived").value(8000.0))
                .andExpect(jsonPath("$.collectionRate").value(66.67))
                .andExpect(jsonPath("$.unpaid").value(4000.0))
                .andExpect(jsonPath("$.overdueOver7Days").value(4000.0))
                .andExpect(jsonPath("$.overdueOver30Days").value(4000.0))
                .andExpect(jsonPath("$.overdueOver60Days").value(0.0))
                .andExpect(jsonPath("$.plans[0].schedules[0].status").value("PAID"))
                .andExpect(jsonPath("$.plans[0].schedules[2].status").value("OVERDUE"));
        mvc.perform(get("/api/v1/public/finance/installment-plans").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].remainingAmount").value(4000.0));

        mvc.perform(
                        post("/api/v1/billing/payments/{paymentId}/reverse", paymentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Virement rejeté par la banque\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduleAllocations.length()").value(2));
        mvc.perform(
                        get("/api/v1/billing/collections/dashboard")
                                .queryParam("asOf", "2026-08-09")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived").value(0.0))
                .andExpect(jsonPath("$.unpaid").value(12000.0));
    }

    @Test
    void customSchedulesAndReminderActionsAreConfigurableIdempotentAndAudited() throws Exception {
        String accountId = createFamilyAccount();
        MvcResult plan =
                mvc.perform(
                                post(
                                                "/api/v1/billing/collections/accounts/{accountId}/installment-plans",
                                                accountId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "Plan personnalisé",
                                                                "frequency",
                                                                "custom",
                                                                "totalAmount",
                                                                "1000.00",
                                                                "startsOn",
                                                                "2026-08-10",
                                                                "installments",
                                                                List.of(
                                                                        Map.of(
                                                                                "dueDate",
                                                                                "2026-08-10",
                                                                                "amount",
                                                                                "400.00"),
                                                                        Map.of(
                                                                                "dueDate",
                                                                                "2026-09-10",
                                                                                "amount",
                                                                                "600.00"))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.frequency").value("CUSTOM"))
                        .andExpect(jsonPath("$.schedules.length()").value(2))
                        .andReturn();
        String scheduleId =
                JsonPath.read(plan.getResponse().getContentAsString(), "$.schedules[0].id");
        MvcResult rule =
                mvc.perform(
                                post("/api/v1/billing/collections/reminder-rules")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "name",
                                                                "J-3 courriel",
                                                                "offsetDays",
                                                                -3,
                                                                "channel",
                                                                "email",
                                                                "automatic",
                                                                true,
                                                                "active",
                                                                true,
                                                                "templateKey",
                                                                "finance-j-minus-3"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.channel").value("EMAIL"))
                        .andReturn();
        String ruleId = JsonPath.read(rule.getResponse().getContentAsString(), "$.id");

        mvc.perform(
                        post("/api/v1/billing/collections/reminders/run")
                                .queryParam("asOf", "2026-08-09")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdActions").value(1));
        mvc.perform(
                        post("/api/v1/billing/collections/reminders/run")
                                .queryParam("asOf", "2026-08-09")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdActions").value(0))
                .andExpect(jsonPath("$.skippedActions").value(1));
        mvc.perform(
                        post(
                                        "/api/v1/billing/collections/payment-schedules/{scheduleId}/reminders",
                                        scheduleId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"channel\":\"sms\",\"message\":\"Relance contrôlée\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.actionType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("PREVIEW"))
                .andExpect(jsonPath("$.adapterKey").value("preview:sms"));
        mvc.perform(
                        put("/api/v1/billing/collections/reminder-rules/{ruleId}", ruleId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "name",
                                                        "J-3 courriel",
                                                        "offsetDays",
                                                        -3,
                                                        "channel",
                                                        "email",
                                                        "automatic",
                                                        false,
                                                        "active",
                                                        true,
                                                        "templateKey",
                                                        "finance-j-minus-3"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automatic").value(false));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=?"
                                        + " AND action LIKE 'billing.%'",
                                Integer.class, tenantId))
                .isGreaterThanOrEqualTo(6);
    }

    @Test
    void creditsRefundsAndAdjustmentsRemainBalancedAndAudited() throws Exception {
        String accountId = createFamilyAccount();
        String invoiceId = createAndIssueInvoice(accountId, "Activité", "500.00", "0.00");
        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/credits", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "200.00",
                                                        "reason",
                                                        "Geste commercial"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.balance").value(300.0));
        UUID creditId =
                jdbc.queryForObject(
                        "SELECT id FROM billing_credit WHERE tenant_id=?", UUID.class, tenantId);
        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/refunds", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "creditId",
                                                        creditId,
                                                        "amount",
                                                        "50.00",
                                                        "refundMethod",
                                                        "cash",
                                                        "reason",
                                                        "Restitution partielle"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundNumber").isNotEmpty());
        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/adjustments", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "invoiceId",
                                                        invoiceId,
                                                        "direction",
                                                        "debit",
                                                        "amount",
                                                        "25.00",
                                                        "reason",
                                                        "Régularisation contrôlée"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.summary.balance").value(375.0))
                .andExpect(jsonPath("$.invoices[0].balanceAmount").value(525.0));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM identity_audit_log WHERE tenant_id=?"
                                        + " AND action LIKE 'billing.%'",
                                Integer.class, tenantId))
                .isGreaterThanOrEqualTo(5);
    }

    @Test
    void permissionsTenantAndParentBoundariesAreEnforced() throws Exception {
        String accountId = createFamilyAccount();
        String invoiceId = createAndIssueInvoice(accountId, "Cantine", "300.00", "0.00");
        mvc.perform(
                        post("/api/v1/billing/accounts/{accountId}/payments", accountId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "400.00",
                                                        "paymentMethod",
                                                        "cash",
                                                        "allocations",
                                                        List.of(
                                                                Map.of(
                                                                        "invoiceId",
                                                                        invoiceId,
                                                                        "amount",
                                                                        "400.00"))))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/public/finance")).andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/billing/accounts/{accountId}", UUID.randomUUID())
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/v1/public/finance/receipts/{receiptId}", UUID.randomUUID())
                                .cookie(parentCookie))
                .andExpect(status().isNotFound());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_payment WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isZero();
    }

    @Test
    void signedWebhookIsIdempotentAndBooksTheOnlinePaymentOnce() throws Exception {
        String accountId = createFamilyAccount();
        String invoiceId = createAndIssueInvoice(accountId, "Scolarité en ligne", "900.00", "0.00");
        MvcResult initialized =
                mvc.perform(
                                post("/api/v1/public/finance/online-payments")
                                        .cookie(parentCookie)
                                        .header("Idempotency-Key", "online-init-2026-0001")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "providerKey",
                                                                "sandbox",
                                                                "amount",
                                                                "900.00",
                                                                "invoiceId",
                                                                invoiceId,
                                                                "returnUrl",
                                                                "/finance"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.providerKey").value("sandbox"))
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andExpect(jsonPath("$.checkoutUrl").isNotEmpty())
                        .andReturn();
        String onlinePaymentId =
                JsonPath.read(initialized.getResponse().getContentAsString(), "$.id");
        String providerPaymentId =
                JsonPath.read(
                        initialized.getResponse().getContentAsString(), "$.providerPaymentId");

        mvc.perform(
                        post("/api/v1/public/finance/online-payments")
                                .cookie(parentCookie)
                                .header("Idempotency-Key", "online-init-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "providerKey",
                                                        "sandbox",
                                                        "amount",
                                                        "900.00",
                                                        "invoiceId",
                                                        invoiceId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(onlinePaymentId));

        String rejectedEvent = "evt-rejected-0001";
        mvc.perform(
                        post("/api/v1/payments/webhooks/sandbox")
                                .header("X-SchoolZ-Event-Id", rejectedEvent)
                                .header("X-SchoolZ-Signature", "invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());

        String eventId = "evt-paid-0001";
        String payload =
                json.writeValueAsString(
                        Map.of(
                                "eventId",
                                eventId,
                                "paymentId",
                                providerPaymentId,
                                "status",
                                "PAID",
                                "amount",
                                "900.00",
                                "currency",
                                "MAD"));
        String signature = hmac(payload);
        mvc.perform(
                        post("/api/v1/payments/webhooks/sandbox")
                                .header("X-SchoolZ-Event-Id", eventId)
                                .header("X-SchoolZ-Signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.duplicate").value(false));
        mvc.perform(
                        post("/api/v1/payments/webhooks/sandbox")
                                .header("X-SchoolZ-Event-Id", eventId)
                                .header("X-SchoolZ-Signature", signature)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.deliveryCount").value(2));

        mvc.perform(get("/api/v1/public/finance/online-payments").cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(onlinePaymentId))
                .andExpect(jsonPath("$[0].status").value("PAID"))
                .andExpect(jsonPath("$[0].resultingPaymentId").isNotEmpty());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_payment WHERE tenant_id=?"
                                        + " AND payment_method='ONLINE_PAYMENT'",
                                Integer.class,
                                tenantId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT balance_amount FROM billing_invoice WHERE tenant_id=?"
                                        + " AND id=?",
                                java.math.BigDecimal.class,
                                tenantId,
                                UUID.fromString(invoiceId)))
                .isEqualByComparingTo("0.00");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_payment_webhook"
                                        + " WHERE processing_status='REJECTED'",
                                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void providerRefundCreatesACompensatingLedgerPayment() throws Exception {
        String accountId = createFamilyAccount();
        String invoiceId = createAndIssueInvoice(accountId, "Transport en ligne", "700.00", "0.00");
        String onlinePaymentId =
                initializeAndCompleteSandbox(invoiceId, "700.00", "refund-flow-01");

        mvc.perform(
                        post("/api/v1/billing/online-payments/{paymentId}/refunds", onlinePaymentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .header("Idempotency-Key", "online-refund-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "700.00",
                                                        "reason",
                                                        "Remboursement sandbox contrôlé"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(
                        jsonPath("$.providerRefundId")
                                .value(org.hamcrest.Matchers.startsWith("sbx_ref_")))
                .andExpect(jsonPath("$.resultingReversalPaymentId").isNotEmpty());
        mvc.perform(
                        post("/api/v1/billing/online-payments/{paymentId}/refunds", onlinePaymentId)
                                .header(HttpHeaders.AUTHORIZATION, bearer())
                                .header("Idempotency-Key", "online-refund-2026-0001")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json.writeValueAsString(
                                                Map.of(
                                                        "amount",
                                                        "700.00",
                                                        "reason",
                                                        "Remboursement sandbox contrôlé"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(
                        get("/api/v1/billing/online-payments")
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REFUNDED"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT balance_amount FROM billing_invoice WHERE tenant_id=?"
                                        + " AND id=?",
                                java.math.BigDecimal.class,
                                tenantId,
                                UUID.fromString(invoiceId)))
                .isEqualByComparingTo("700.00");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_payment WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM billing_online_refund WHERE tenant_id=?",
                                Integer.class,
                                tenantId))
                .isEqualTo(1);
    }

    private String initializeAndCompleteSandbox(String invoiceId, String amount, String key)
            throws Exception {
        MvcResult initialized =
                mvc.perform(
                                post("/api/v1/public/finance/online-payments")
                                        .cookie(parentCookie)
                                        .header("Idempotency-Key", key)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "providerKey",
                                                                "sandbox",
                                                                "amount",
                                                                amount,
                                                                "invoiceId",
                                                                invoiceId))))
                        .andExpect(status().isCreated())
                        .andReturn();
        String id = JsonPath.read(initialized.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post(
                                        "/api/v1/public/finance/online-payments/{paymentId}/sandbox-complete",
                                        id)
                                .cookie(parentCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        return id;
    }

    private static String hmac(String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SANDBOX_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private String createFamilyAccount() throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/billing/accounts")
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "portalAccountId",
                                                                portalAccountId,
                                                                "displayName",
                                                                "Famille Alaoui"))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.accountNumber").isNotEmpty())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createAndIssueInvoice(
            String accountId, String label, String amount, String discount) throws Exception {
        MvcResult draft =
                mvc.perform(
                                post("/api/v1/billing/accounts/{accountId}/invoices", accountId)
                                        .header(HttpHeaders.AUTHORIZATION, bearer())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "issueDate",
                                                                "2026-08-01",
                                                                "dueDate",
                                                                "2026-09-05",
                                                                "lines",
                                                                List.of(
                                                                        Map.of(
                                                                                "studentId",
                                                                                studentId,
                                                                                "serviceType",
                                                                                "other_fee",
                                                                                "label",
                                                                                label,
                                                                                "quantity",
                                                                                "1",
                                                                                "unitAmount",
                                                                                amount,
                                                                                "discountAmount",
                                                                                discount))))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.status").value("DRAFT"))
                        .andReturn();
        String invoiceId = JsonPath.read(draft.getResponse().getContentAsString(), "$.id");
        mvc.perform(
                        post("/api/v1/billing/invoices/{invoiceId}/issue", invoiceId)
                                .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ISSUED"));
        return invoiceId;
    }

    private String login(String tenantSlug, String email, String password) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                tenantSlug,
                                                                "email",
                                                                email,
                                                                "password",
                                                                password))))
                        .andExpect(status().isOk())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private Cookie registerParent(String tenantSlug, String email) throws Exception {
        MvcResult result =
                mvc.perform(
                                post("/api/v1/public/admissions/accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsString(
                                                        Map.of(
                                                                "tenantSlug",
                                                                tenantSlug,
                                                                "email",
                                                                email,
                                                                "password",
                                                                PARENT_PASSWORD,
                                                                "displayName",
                                                                "Famille Alaoui"))))
                        .andExpect(status().isOk())
                        .andReturn();
        return result.getResponse().getCookie("schoolz_admission");
    }

    private UUID createStudent(UUID ownerTenantId, String firstName, String lastName) {
        Instant now = clock.instant();
        UUID personId = UUID.randomUUID();
        UUID createdStudentId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person"
                    + " (id,tenant_id,first_name,last_name,birth_date,status,created_at,updated_at)"
                    + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?)",
                personId,
                ownerTenantId,
                firstName,
                lastName,
                LocalDate.of(2017, 4, 12),
                timestamp(now),
                timestamp(now));
        jdbc.update(
                "INSERT INTO student_profile"
                        + " (id,tenant_id,person_id,status,created_at,updated_at)"
                        + " VALUES (?, ?, ?, 'ACTIVE', ?, ?)",
                createdStudentId,
                ownerTenantId,
                personId,
                timestamp(now),
                timestamp(now));
        return createdStudentId;
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private void cleanDatabase() {
        jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
        try {
            for (String table :
                    jdbc.queryForList(
                            "SELECT table_name FROM information_schema.tables WHERE"
                                    + " table_schema='public' AND table_type='BASE TABLE'",
                            String.class)) {
                if (!"flyway_schema_history".equalsIgnoreCase(table)
                        && !"platform_metadata".equalsIgnoreCase(table)) {
                    jdbc.update("DELETE FROM " + table);
                }
            }
        } finally {
            jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
