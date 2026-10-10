package ma.schoolz.erp.procurement.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.accounting.ProcurementAccountingPort;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.AccountView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.CostCenterView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.JournalView;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.ReferenceData;
import ma.schoolz.erp.accounting.ProcurementAccountingPort.SupplierView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.procurement.application.ProcurementService.ConfigurationCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.DecisionCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.InvoiceCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.InvoiceMatchView;
import ma.schoolz.erp.procurement.application.ProcurementService.OrderCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.OrderView;
import ma.schoolz.erp.procurement.application.ProcurementService.PaymentCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.ReceiptCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.ReceiptLineCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.RequestCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.RequestLineCommand;
import ma.schoolz.erp.procurement.application.ProcurementService.RequestView;
import ma.schoolz.erp.procurement.application.ProcurementService.ThresholdCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(240)
public class DevProcurementSeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "procurement.read",
                    "procurement.request",
                    "procurement.approve",
                    "procurement.order",
                    "procurement.receive",
                    "procurement.invoice",
                    "procurement.pay",
                    "procurement.configure");

    private final JdbcTemplate jdbc;
    private final ProcurementService procurement;
    private final ProcurementAccountingPort accounting;
    private final boolean enabled;
    private final String tenantSlug;

    public DevProcurementSeeder(
            JdbcTemplate jdbc,
            ProcurementService procurement,
            ProcurementAccountingPort accounting,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.procurement = procurement;
        this.accounting = accounting;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM procurement_configuration WHERE tenant_id=?", tenantId)
                > 0) {
            return;
        }
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email=?",
                        tenantId,
                        "admin@schoolz.ma");
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Démonstration achats",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        PERMISSIONS);
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext seeded = SecurityContextHolder.createEmptyContext();
        seeded.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        actor,
                        null,
                        PERMISSIONS.stream().map(SimpleGrantedAuthority::new).toList()));
        SecurityContextHolder.setContext(seeded);
        try {
            String ip = "127.0.0.1";
            procurement.configure(actor, new ConfigurationCommand("MAD", true), ip);
            procurement.createThreshold(
                    actor,
                    new ThresholdCommand(
                            "COURANT",
                            "Achat courant — une validation",
                            BigDecimal.ZERO,
                            new BigDecimal("5000.00"),
                            1),
                    ip);
            procurement.createThreshold(
                    actor,
                    new ThresholdCommand(
                            "ENCADRE",
                            "Achat encadré — une validation",
                            new BigDecimal("5000.00"),
                            new BigDecimal("25000.00"),
                            1),
                    ip);
            procurement.createThreshold(
                    actor,
                    new ThresholdCommand(
                            "STRATEGIQUE",
                            "Achat stratégique — double validation",
                            new BigDecimal("25000.00"),
                            null,
                            2),
                    ip);

            ReferenceData references = accounting.procurementReferences(actor);
            SupplierView supplier =
                    references.suppliers().stream()
                            .filter(item -> "FOUR-DEMO-01".equals(item.code()))
                            .findFirst()
                            .orElseThrow();
            JournalView purchaseJournal = journal(references, "ACH");
            JournalView bankJournal = journal(references, "BNK");
            AccountView supplies = account(references, "614100");
            AccountView payable = account(references, "441100");
            AccountView bank = account(references, "111100");
            CostCenterView primary = costCenter(references, "PRIMAIRE");

            RequestView paidRequest =
                    procurement.createRequest(
                            actor,
                            new RequestCommand(
                                    primary.id(),
                                    LocalDate.of(2026, 8, 10),
                                    "Kits créatifs pour les classes de primaire",
                                    "Équiper les ateliers de rentrée avec du matériel durable et"
                                            + " mutualisé.",
                                    "HIGH",
                                    List.of(
                                            new RequestLineCommand(
                                                    "Kits de géométrie et création",
                                                    new BigDecimal("30"),
                                                    "kit",
                                                    new BigDecimal("320.00")),
                                            new RequestLineCommand(
                                                    "Tableaux magnétiques mobiles",
                                                    new BigDecimal("6"),
                                                    "unité",
                                                    new BigDecimal("500.00")))),
                            ip);
            paidRequest = procurement.submitRequest(actor, paidRequest.id(), ip);
            paidRequest =
                    procurement.decideRequest(
                            actor,
                            paidRequest.id(),
                            new DecisionCommand(
                                    "APPROVED", "Budget pédagogique contrôlé et disponible."),
                            ip);
            OrderView order =
                    procurement.createOrder(
                            actor,
                            paidRequest.id(),
                            new OrderCommand(
                                    supplier.id(),
                                    LocalDate.of(2026, 8, 11),
                                    LocalDate.of(2026, 8, 20),
                                    "Campus Casablanca Centre — accueil marchandises",
                                    "Livraison avant la pré-rentrée"),
                            ip);
            procurement.receiveGoods(
                    actor,
                    order.id(),
                    new ReceiptCommand(
                            LocalDate.of(2026, 8, 18),
                            "BL-ATLAS-2026-118",
                            "Quantités et état contrôlés à la réception.",
                            order.lines().stream()
                                    .map(
                                            line ->
                                                    new ReceiptLineCommand(
                                                            line.id(), line.quantityOrdered()))
                                    .toList()),
                    ip);
            InvoiceMatchView invoice =
                    procurement.recordInvoice(
                            actor,
                            order.id(),
                            new InvoiceCommand(
                                    purchaseJournal.id(),
                                    supplies.id(),
                                    payable.id(),
                                    primary.id(),
                                    "FA-ATLAS-2026-118",
                                    LocalDate.of(2026, 8, 19),
                                    LocalDate.of(2026, 9, 18),
                                    "Kits créatifs et tableaux magnétiques — commande"
                                            + " démonstration",
                                    new BigDecimal("12600.00")),
                            ip);
            procurement.payInvoice(
                    actor,
                    invoice.id(),
                    new PaymentCommand(
                            bankJournal.id(),
                            payable.id(),
                            bank.id(),
                            LocalDate.of(2026, 8, 25),
                            "VIR-ATLAS-2026-118",
                            new BigDecimal("12600.00"),
                            "BANK_TRANSFER"),
                    ip);

            RequestView strategic =
                    procurement.createRequest(
                            actor,
                            new RequestCommand(
                                    primary.id(),
                                    LocalDate.of(2026, 8, 10),
                                    "Équipement du laboratoire scientifique",
                                    "Renouveler les instruments de mesure du laboratoire pour"
                                            + " l’année scolaire.",
                                    "NORMAL",
                                    List.of(
                                            new RequestLineCommand(
                                                    "Lot d’instruments scientifiques",
                                                    BigDecimal.ONE,
                                                    "lot",
                                                    new BigDecimal("38000.00")))),
                            ip);
            strategic = procurement.submitRequest(actor, strategic.id(), ip);
            procurement.decideRequest(
                    actor,
                    strategic.id(),
                    new DecisionCommand("APPROVED", "Première validation — direction pédagogique."),
                    ip);
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private JournalView journal(ReferenceData references, String code) {
        return references.journals().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private AccountView account(ReferenceData references, String code) {
        return references.accounts().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private CostCenterView costCenter(ReferenceData references, String code) {
        return references.costCenters().stream()
                .filter(item -> code.equals(item.code()))
                .findFirst()
                .orElseThrow();
    }

    private UUID id(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, UUID.class, arguments);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
