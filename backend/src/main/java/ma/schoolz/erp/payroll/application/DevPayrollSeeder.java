package ma.schoolz.erp.payroll.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.payroll.application.PayrollService.AdjustmentCommand;
import ma.schoolz.erp.payroll.application.PayrollService.ComponentCommand;
import ma.schoolz.erp.payroll.application.PayrollService.ComponentView;
import ma.schoolz.erp.payroll.application.PayrollService.PeriodCommand;
import ma.schoolz.erp.payroll.application.PayrollService.PeriodView;
import ma.schoolz.erp.payroll.application.PayrollService.ProfileCommand;
import ma.schoolz.erp.payroll.application.PayrollService.ProfileComponentCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RuleCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RuleView;
import ma.schoolz.erp.payroll.application.PayrollService.RunCommand;
import ma.schoolz.erp.payroll.application.PayrollService.RunView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(220)
public class DevPayrollSeeder implements ApplicationRunner {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "payroll.read",
                    "payroll.configure",
                    "payroll.run",
                    "payroll.finalize",
                    "payroll.export");

    private final JdbcTemplate jdbc;
    private final PayrollService payroll;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevPayrollSeeder(
            JdbcTemplate jdbc,
            PayrollService payroll,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:ecole-demo}") String tenantSlug) {
        this.jdbc = jdbc;
        this.payroll = payroll;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId = id("SELECT id FROM identity_tenant WHERE slug=?", tenantSlug);
        if (count("SELECT count(*) FROM payroll_period WHERE tenant_id=?", tenantId) > 0) return;
        UUID adminId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        SchoolPrincipal actor =
                new SchoolPrincipal(
                        adminId,
                        tenantId,
                        "admin@schoolz.ma",
                        "Démonstration paie",
                        "fr-MA",
                        Set.of("SUPER_ADMIN"),
                        PERMISSIONS);

        PeriodView period =
                payroll.createPeriod(
                        actor,
                        new PeriodCommand(
                                "2026-08",
                                "Paie août 2026",
                                LocalDate.of(2026, 8, 1),
                                LocalDate.of(2026, 8, 31),
                                LocalDate.of(2026, 8, 31),
                                "MAD",
                                "OPEN"),
                        "127.0.0.1");

        ComponentView base =
                component(
                        actor,
                        "BASE_SALARY",
                        "Salaire de base",
                        "BASE",
                        "PROFILE_AMOUNT",
                        "PAYROLL_EXPENSE",
                        "PAYROLL_PAYABLE",
                        10);
        ComponentView allowance =
                component(
                        actor,
                        "DEMO_ALLOWANCE",
                        "Indemnité interne de démonstration",
                        "ALLOWANCE",
                        "FIXED",
                        "PAYROLL_EXPENSE",
                        "PAYROLL_PAYABLE",
                        20);
        ComponentView bonus =
                component(
                        actor,
                        "DEMO_BONUS",
                        "Prime ponctuelle de démonstration",
                        "BONUS",
                        "FIXED",
                        "PAYROLL_EXPENSE",
                        "PAYROLL_PAYABLE",
                        30);
        ComponentView overtime =
                component(
                        actor,
                        "OVERTIME",
                        "Heures supplémentaires",
                        "OVERTIME",
                        "PER_UNIT",
                        "PAYROLL_EXPENSE",
                        "PAYROLL_PAYABLE",
                        40);
        ComponentView absence =
                component(
                        actor,
                        "ABSENCE",
                        "Retenue absence",
                        "ABSENCE",
                        "PER_UNIT",
                        "PAYROLL_PAYABLE",
                        "PAYROLL_DEDUCTION",
                        50);
        component(
                actor,
                "OTHER_DEDUCTION",
                "Autre retenue",
                "DEDUCTION",
                "FIXED",
                "PAYROLL_PAYABLE",
                "PAYROLL_DEDUCTION",
                60);

        RuleView allowanceRule =
                rule(actor, allowance.id(), "DEMO_ALLOWANCE_FIXED", "FIXED", "300.00");
        rule(actor, overtime.id(), "DEMO_OVERTIME_UNIT", "PER_UNIT", "80.00");
        rule(actor, absence.id(), "DEMO_ABSENCE_UNIT", "PER_UNIT", "65.00");

        createProfile(
                actor,
                employee(tenantId, "DEMO-TEACH-001"),
                "SAL-FOUAD",
                "14500",
                base,
                allowance,
                allowanceRule);
        UUID nadia = employee(tenantId, "DEMO-TEACH-002");
        createProfile(actor, nadia, "SAL-NADIA", "10500", base, allowance, allowanceRule);
        UUID leila = employee(tenantId, "DEMO-TEACH-003");
        createProfile(actor, leila, "SAL-LEILA", "9000", base, allowance, allowanceRule);
        UUID salma = employee(tenantId, "EDU-CRECHE-001");
        createProfile(actor, salma, "SAL-SALMA", "7800", base, allowance, allowanceRule);

        payroll.createAdjustment(
                actor,
                new AdjustmentCommand(
                        leila,
                        period.id(),
                        overtime.id(),
                        new BigDecimal("4"),
                        new BigDecimal("80"),
                        null,
                        "4 heures supplémentaires — valeur de démonstration non réglementaire"),
                "127.0.0.1");
        payroll.createAdjustment(
                actor,
                new AdjustmentCommand(
                        nadia,
                        period.id(),
                        bonus.id(),
                        null,
                        null,
                        new BigDecimal("500"),
                        "Prime ponctuelle interne de démonstration"),
                "127.0.0.1");
        payroll.createAdjustment(
                actor,
                new AdjustmentCommand(
                        salma,
                        period.id(),
                        absence.id(),
                        new BigDecimal("0.5"),
                        new BigDecimal("65"),
                        null,
                        "Demi-unité d’absence — valeur de démonstration à valider"),
                "127.0.0.1");

        RunView run =
                payroll.createRun(
                        actor, new RunCommand(period.id(), "RUN-2026-08-01"), "127.0.0.1");
        payroll.calculateRun(actor, run.id(), "127.0.0.1");
    }

    private ComponentView component(
            SchoolPrincipal actor,
            String code,
            String name,
            String type,
            String basis,
            String expenseAccount,
            String liabilityAccount,
            int order) {
        return payroll.createComponent(
                actor,
                new ComponentCommand(
                        code,
                        name,
                        type,
                        basis,
                        "Paramètre interne de démonstration, sans valeur juridique ou fiscale.",
                        expenseAccount,
                        liabilityAccount,
                        order),
                "127.0.0.1");
    }

    private RuleView rule(
            SchoolPrincipal actor, UUID componentId, String code, String basis, String value) {
        return payroll.createRule(
                actor,
                new RuleCommand(
                        componentId,
                        code,
                        "Règle de démonstration à valider",
                        100,
                        "FIXED".equals(basis) ? new BigDecimal(value) : null,
                        null,
                        "PER_UNIT".equals(basis) ? new BigDecimal(value) : null,
                        null,
                        "Paramétrage SchoolZ de démonstration — aucune source réglementaire",
                        false,
                        LocalDate.of(2026, 1, 1),
                        null),
                "127.0.0.1");
    }

    private void createProfile(
            SchoolPrincipal actor,
            UUID employeeId,
            String code,
            String baseAmount,
            ComponentView base,
            ComponentView allowance,
            RuleView allowanceRule) {
        payroll.createProfile(
                actor,
                new ProfileCommand(
                        employeeId,
                        code,
                        "MAD",
                        LocalDate.of(2026, 1, 1),
                        null,
                        "BANK_TRANSFER",
                        "Profil contractuel de démonstration, hors calcul fiscal et social.",
                        List.of(
                                new ProfileComponentCommand(
                                        base.id(), null, new BigDecimal(baseAmount), null, null),
                                new ProfileComponentCommand(
                                        allowance.id(), allowanceRule.id(), null, null, null))),
                "127.0.0.1");
    }

    private UUID employee(UUID tenantId, String employeeNumber) {
        return id(
                """
                SELECT e.id FROM hr_employee e
                JOIN staff_profile s ON s.id=e.staff_profile_id AND s.tenant_id=e.tenant_id
                WHERE e.tenant_id=? AND s.employee_number=?
                """,
                tenantId,
                employeeNumber);
    }

    private UUID id(String sql, Object... arguments) {
        UUID value = jdbc.queryForObject(sql, UUID.class, arguments);
        if (value == null) throw new IllegalStateException("Donnée de démonstration introuvable");
        return value;
    }

    private int count(String sql, Object... arguments) {
        Integer value = jdbc.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }
}
