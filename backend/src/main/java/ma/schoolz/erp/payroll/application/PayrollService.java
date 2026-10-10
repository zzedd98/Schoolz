package ma.schoolz.erp.payroll.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollService {
    public static final String COMPLIANCE_NOTICE =
            "Configuration de démonstration non validée juridiquement. Les règles fiscales et"
                    + " sociales marocaines doivent être sourcées, vérifiées et approuvées avant"
                    + " production.";

    private static final Set<String> PERIOD_STATUSES =
            Set.of("DRAFT", "OPEN", "CALCULATED", "FINALIZED", "EXPORTED");
    private static final Set<String> COMPONENT_TYPES =
            Set.of("BASE", "BONUS", "DEDUCTION", "OVERTIME", "ABSENCE", "ALLOWANCE");
    private static final Set<String> CALCULATION_BASES =
            Set.of("PROFILE_AMOUNT", "FIXED", "PERCENT_OF_BASE", "PER_UNIT");
    private static final Set<String> RUN_STATUSES =
            Set.of("DRAFT", "CALCULATING", "CALCULATED", "FINALIZED", "EXPORTED");
    private static final Set<String> EXPORT_FORMATS = Set.of("CSV", "JSON");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final IdentityAuditTrail audit;

    public PayrollService(JdbcTemplate jdbc, Clock clock, IdentityAuditTrail audit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public Dashboard dashboard(SchoolPrincipal actor, String ipAddress) {
        require(actor, "payroll.read");
        Dashboard result =
                new Dashboard(
                        clock.instant(),
                        COMPLIANCE_NOTICE,
                        periods(actor.tenantId()),
                        components(actor.tenantId()),
                        rules(actor.tenantId()),
                        profiles(actor.tenantId()),
                        runs(actor.tenantId()),
                        adjustments(actor.tenantId(), null));
        audit(actor, "payroll.dashboard.viewed", "PayrollDashboard", actor.tenantId(), ipAddress);
        return result;
    }

    @Transactional
    public PeriodView createPeriod(SchoolPrincipal actor, PeriodCommand command, String ipAddress) {
        require(actor, "payroll.configure");
        if (command == null
                || command.startsOn() == null
                || command.endsOn() == null
                || command.paymentDate() == null) {
            throw new PayrollValidationException("Période, dates et date de paiement requises");
        }
        if (command.endsOn().isBefore(command.startsOn())) {
            throw new PayrollValidationException("La fin de période précède son début");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO payroll_period(
                        id,tenant_id,code,label,starts_on,ends_on,payment_date,currency,status,
                        created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    code(command.code()),
                    required(command.label(), "Libellé de période requis"),
                    command.startsOn(),
                    command.endsOn(),
                    command.paymentDate(),
                    currency(command.currency()),
                    enumValue(command.status(), PERIOD_STATUSES, "Statut de période invalide"),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new PayrollValidationException("Ce code de période existe déjà");
        }
        audit(actor, "payroll.period.created", "PayrollPeriod", id, ipAddress);
        return period(actor.tenantId(), id);
    }

    @Transactional
    public ComponentView createComponent(
            SchoolPrincipal actor, ComponentCommand command, String ipAddress) {
        require(actor, "payroll.configure");
        if (command == null) throw new PayrollValidationException("Composant salarial requis");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO payroll_salary_component(
                        id,tenant_id,code,name,component_type,calculation_basis,description,
                        expense_account_code,liability_account_code,active,display_order,
                        created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,TRUE,?,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    code(command.code()),
                    required(command.name(), "Nom du composant requis"),
                    enumValue(
                            command.componentType(), COMPONENT_TYPES, "Type de composant invalide"),
                    enumValue(
                            command.calculationBasis(),
                            CALCULATION_BASES,
                            "Base de calcul invalide"),
                    nullable(command.description()),
                    nullable(command.expenseAccountCode()),
                    nullable(command.liabilityAccountCode()),
                    command.displayOrder() == null ? 0 : command.displayOrder(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new PayrollValidationException("Ce code de composant existe déjà");
        }
        audit(actor, "payroll.component.created", "SalaryComponent", id, ipAddress);
        return component(actor.tenantId(), id);
    }

    @Transactional
    public RuleView createRule(SchoolPrincipal actor, RuleCommand command, String ipAddress) {
        require(actor, "payroll.configure");
        if (command == null || command.componentId() == null || command.effectiveFrom() == null) {
            throw new PayrollValidationException("Composant et date d’effet requis");
        }
        ComponentView component = component(actor.tenantId(), command.componentId());
        validateRuleValues(component.calculationBasis(), command);
        if (command.legallyValidated() && nullable(command.sourceReference()) == null) {
            throw new PayrollValidationException(
                    "Une règle déclarée validée doit référencer une source contrôlée");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO payroll_salary_rule(
                        id,tenant_id,component_id,code,name,priority,fixed_amount,rate_percent,
                        unit_rate,condition_expression,source_reference,legally_validated,
                        effective_from,effective_to,active,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    command.componentId(),
                    code(command.code()),
                    required(command.name(), "Nom de règle requis"),
                    command.priority() == null ? 100 : command.priority(),
                    moneyOrNull(command.fixedAmount()),
                    nonNegative(command.ratePercent(), "Taux invalide"),
                    nonNegative(command.unitRate(), "Tarif unitaire invalide"),
                    nullable(command.conditionExpression()),
                    nullable(command.sourceReference()),
                    command.legallyValidated(),
                    command.effectiveFrom(),
                    command.effectiveTo(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new PayrollValidationException("Ce code de règle existe déjà");
        }
        audit(actor, "payroll.rule.created", "SalaryRule", id, ipAddress);
        return rule(actor.tenantId(), id);
    }

    @Transactional
    public SalaryProfileView createProfile(
            SchoolPrincipal actor, ProfileCommand command, String ipAddress) {
        require(actor, "payroll.configure");
        if (command == null || command.employeeId() == null || command.effectiveFrom() == null) {
            throw new PayrollValidationException("Employé et date d’effet requis");
        }
        employeeName(actor.tenantId(), command.employeeId());
        if (command.components() == null || command.components().isEmpty()) {
            throw new PayrollValidationException("Le profil doit contenir au moins un composant");
        }
        UUID profileId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.update(
                    """
                    INSERT INTO payroll_employee_salary_profile(
                        id,tenant_id,employee_id,code,currency,effective_from,effective_to,active,
                        payment_method,notes,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,TRUE,?,?,?,?)
                    """,
                    profileId,
                    actor.tenantId(),
                    command.employeeId(),
                    code(command.code()),
                    currency(command.currency()),
                    command.effectiveFrom(),
                    command.effectiveTo(),
                    nullable(command.paymentMethod()),
                    nullable(command.notes()),
                    timestamp(now),
                    timestamp(now));
            for (ProfileComponentCommand line : command.components()) {
                addProfileComponent(actor.tenantId(), profileId, line, now);
            }
        } catch (DuplicateKeyException exception) {
            throw new PayrollValidationException(
                    "Le code de profil ou l’un de ses composants existe déjà");
        }
        audit(actor, "payroll.profile.created", "EmployeeSalaryProfile", profileId, ipAddress);
        return profile(actor.tenantId(), profileId);
    }

    @Transactional
    public AdjustmentView createAdjustment(
            SchoolPrincipal actor, AdjustmentCommand command, String ipAddress) {
        require(actor, "payroll.run");
        if (command == null
                || command.employeeId() == null
                || command.periodId() == null
                || command.componentId() == null) {
            throw new PayrollValidationException("Employé, période et composant requis");
        }
        employeeName(actor.tenantId(), command.employeeId());
        PeriodView period = period(actor.tenantId(), command.periodId());
        if (!Set.of("DRAFT", "OPEN").contains(period.status())) {
            throw new PayrollValidationException("Cette période n’accepte plus d’ajustement");
        }
        component(actor.tenantId(), command.componentId());
        BigDecimal amount = moneyOrNull(command.amount());
        BigDecimal quantity = nonNegative(command.quantity(), "Quantité invalide");
        BigDecimal unitRate = nonNegative(command.unitRate(), "Tarif unitaire invalide");
        if (amount == null && (quantity == null || unitRate == null)) {
            throw new PayrollValidationException(
                    "Indiquez un montant ou une quantité avec son tarif unitaire");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update(
                """
                INSERT INTO payroll_salary_adjustment(
                    id,tenant_id,employee_id,period_id,component_id,quantity,unit_rate,amount,
                    reason,status,created_by_user_id,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,'PENDING',?,?)
                """,
                id,
                actor.tenantId(),
                command.employeeId(),
                command.periodId(),
                command.componentId(),
                quantity,
                unitRate,
                amount,
                required(command.reason(), "Motif requis"),
                actor.accountId(),
                timestamp(now));
        audit(actor, "payroll.adjustment.created", "SalaryAdjustment", id, ipAddress);
        return adjustment(actor.tenantId(), id);
    }

    @Transactional
    public RunView createRun(SchoolPrincipal actor, RunCommand command, String ipAddress) {
        require(actor, "payroll.run");
        if (command == null || command.periodId() == null) {
            throw new PayrollValidationException("Période de paie requise");
        }
        PeriodView period = period(actor.tenantId(), command.periodId());
        if (!Set.of("DRAFT", "OPEN", "CALCULATED").contains(period.status())) {
            throw new PayrollValidationException(
                    "Cette période ne peut pas recevoir un nouveau run");
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        String number =
                nullable(command.runNumber()) == null
                        ? "RUN-" + period.code() + "-01"
                        : code(command.runNumber());
        try {
            jdbc.update(
                    """
                    INSERT INTO payroll_run(
                        id,tenant_id,period_id,run_number,status,started_by_user_id,started_at,
                        updated_at,version)
                    VALUES(?,?,?,?,'DRAFT',?,?,?,0)
                    """,
                    id,
                    actor.tenantId(),
                    command.periodId(),
                    number,
                    actor.accountId(),
                    timestamp(now),
                    timestamp(now));
        } catch (DuplicateKeyException exception) {
            throw new PayrollValidationException("Un run existe déjà pour cette période");
        }
        audit(actor, "payroll.run.created", "PayrollRun", id, ipAddress);
        return run(actor.tenantId(), id);
    }

    @Transactional
    public RunView calculateRun(SchoolPrincipal actor, UUID runId, String ipAddress) {
        require(actor, "payroll.run");
        RunView existing = run(actor.tenantId(), runId);
        if (!Set.of("DRAFT", "CALCULATED").contains(existing.status())) {
            throw new PayrollValidationException("Ce run ne peut plus être recalculé");
        }
        PeriodView period = period(actor.tenantId(), existing.periodId());
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE payroll_run SET status='CALCULATING',updated_at=?,version=version+1"
                        + " WHERE tenant_id=? AND id=?",
                timestamp(now),
                actor.tenantId(),
                runId);
        jdbc.update(
                "UPDATE payroll_salary_adjustment SET status='PENDING',applied_at=NULL WHERE"
                        + " tenant_id=? AND period_id=? AND status='APPLIED'",
                actor.tenantId(),
                period.id());
        jdbc.update(
                "DELETE FROM payroll_payslip_line WHERE tenant_id=? AND payslip_id IN (SELECT id"
                        + " FROM payroll_payslip WHERE tenant_id=? AND payroll_run_id=?)",
                actor.tenantId(),
                actor.tenantId(),
                runId);
        jdbc.update(
                "DELETE FROM payroll_payslip WHERE tenant_id=? AND payroll_run_id=?",
                actor.tenantId(),
                runId);

        List<ProfileCalculationRow> salaryProfiles =
                calculationProfiles(actor.tenantId(), period.endsOn());
        if (salaryProfiles.isEmpty()) {
            throw new PayrollValidationException("Aucun profil salarial actif sur cette période");
        }
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        int sequence = 1;
        for (ProfileCalculationRow profile : salaryProfiles) {
            Calculation calculation =
                    calculateProfile(actor.tenantId(), profile, period, runId, sequence++, now);
            totalGross = totalGross.add(calculation.gross());
            totalDeductions = totalDeductions.add(calculation.deductions());
            totalNet = totalNet.add(calculation.net());
        }
        jdbc.update(
                """
                UPDATE payroll_run SET status='CALCULATED',employee_count=?,total_gross=?,
                    total_deductions=?,total_net=?,calculated_at=?,updated_at=?,version=version+1
                WHERE tenant_id=? AND id=?
                """,
                salaryProfiles.size(),
                money(totalGross),
                money(totalDeductions),
                money(totalNet),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                runId);
        jdbc.update(
                "UPDATE payroll_period SET status='CALCULATED',updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                timestamp(now),
                actor.tenantId(),
                period.id());
        audit(actor, "payroll.run.calculated", "PayrollRun", runId, ipAddress);
        return run(actor.tenantId(), runId);
    }

    @Transactional
    public RunView finalizeRun(SchoolPrincipal actor, UUID runId, String ipAddress) {
        require(actor, "payroll.finalize");
        RunView existing = run(actor.tenantId(), runId);
        if (!"CALCULATED".equals(existing.status())) {
            throw new PayrollValidationException("Seul un run calculé peut être finalisé");
        }
        Instant now = clock.instant();
        jdbc.update(
                """
                UPDATE payroll_run SET status='FINALIZED',finalized_by_user_id=?,finalized_at=?,
                    updated_at=?,version=version+1 WHERE tenant_id=? AND id=?
                """,
                actor.accountId(),
                timestamp(now),
                timestamp(now),
                actor.tenantId(),
                runId);
        jdbc.update(
                "UPDATE payroll_payslip SET status='FINALIZED',finalized_at=? WHERE tenant_id=?"
                        + " AND payroll_run_id=?",
                timestamp(now),
                actor.tenantId(),
                runId);
        jdbc.update(
                "UPDATE payroll_period SET status='FINALIZED',updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                timestamp(now),
                actor.tenantId(),
                existing.periodId());
        audit(actor, "payroll.run.finalized", "PayrollRun", runId, ipAddress);
        return run(actor.tenantId(), runId);
    }

    @Transactional
    public List<PayslipSummary> payslips(SchoolPrincipal actor, UUID runId, String ipAddress) {
        require(actor, "payroll.read");
        run(actor.tenantId(), runId);
        List<PayslipSummary> result = payslips(actor.tenantId(), runId);
        audit(actor, "payroll.payslips.viewed", "PayrollRun", runId, ipAddress);
        return result;
    }

    @Transactional
    public PayslipRecord payslip(SchoolPrincipal actor, UUID payslipId, String ipAddress) {
        require(actor, "payroll.read");
        PayslipSummary summary = payslipSummary(actor.tenantId(), payslipId);
        List<PayslipLineView> lines = payslipLines(actor.tenantId(), payslipId);
        audit(actor, "payroll.payslip.viewed", "Payslip", payslipId, ipAddress);
        return new PayslipRecord(summary, lines, COMPLIANCE_NOTICE);
    }

    @Transactional
    public AccountingExportView exportAccounting(
            SchoolPrincipal actor, UUID runId, String format, String ipAddress) {
        require(actor, "payroll.export");
        RunView payrollRun = run(actor.tenantId(), runId);
        if (!Set.of("FINALIZED", "EXPORTED").contains(payrollRun.status())) {
            throw new PayrollValidationException("Le run doit être finalisé avant export");
        }
        String normalizedFormat = enumValue(format, EXPORT_FORMATS, "Format d’export invalide");
        List<ExportLine> rows = exportLines(actor.tenantId(), runId);
        String content = "CSV".equals(normalizedFormat) ? csv(rows) : json(rows);
        String digest = sha256(content);
        List<AccountingExportView> existing =
                jdbc.query(
                        """
                        SELECT id,export_reference,format,content_text,sha256,created_at
                        FROM payroll_accounting_export
                        WHERE tenant_id=? AND payroll_run_id=? AND format=? AND sha256=?
                        """,
                        (result, row) -> accountingExport(result),
                        actor.tenantId(),
                        runId,
                        normalizedFormat,
                        digest);
        AccountingExportView result;
        if (existing.isEmpty()) {
            UUID id = UUID.randomUUID();
            Instant now = clock.instant();
            String reference =
                    "EXP-"
                            + payrollRun.runNumber()
                            + "-"
                            + normalizedFormat
                            + "-"
                            + digest.substring(0, 8).toUpperCase(Locale.ROOT);
            jdbc.update(
                    """
                    INSERT INTO payroll_accounting_export(
                        id,tenant_id,payroll_run_id,export_reference,format,content_text,sha256,
                        created_by_user_id,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?)
                    """,
                    id,
                    actor.tenantId(),
                    runId,
                    reference,
                    normalizedFormat,
                    content,
                    digest,
                    actor.accountId(),
                    timestamp(now));
            result = accountingExport(actor.tenantId(), id);
        } else {
            result = existing.getFirst();
        }
        Instant now = clock.instant();
        jdbc.update(
                "UPDATE payroll_run SET status='EXPORTED',updated_at=? WHERE tenant_id=? AND id=?",
                timestamp(now),
                actor.tenantId(),
                runId);
        jdbc.update(
                "UPDATE payroll_period SET status='EXPORTED',updated_at=? WHERE tenant_id=? AND"
                        + " id=?",
                timestamp(now),
                actor.tenantId(),
                payrollRun.periodId());
        audit(actor, "payroll.accounting.exported", "PayrollRun", runId, ipAddress);
        return result;
    }

    private void addProfileComponent(
            UUID tenantId, UUID profileId, ProfileComponentCommand command, Instant now) {
        if (command == null || command.componentId() == null) {
            throw new PayrollValidationException("Composant de profil requis");
        }
        ComponentView component = component(tenantId, command.componentId());
        if (command.ruleId() != null) rule(tenantId, command.ruleId());
        if ("PROFILE_AMOUNT".equals(component.calculationBasis())
                && command.configuredAmount() == null) {
            throw new PayrollValidationException(
                    "Un montant est requis pour le composant " + component.name());
        }
        jdbc.update(
                """
                INSERT INTO payroll_profile_component(
                    id,tenant_id,profile_id,component_id,rule_id,configured_amount,
                    configured_rate,configured_unit_rate,active,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,TRUE,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                profileId,
                command.componentId(),
                command.ruleId(),
                moneyOrNull(command.configuredAmount()),
                nonNegative(command.configuredRate(), "Taux configuré invalide"),
                nonNegative(command.configuredUnitRate(), "Tarif unitaire configuré invalide"),
                timestamp(now),
                timestamp(now));
    }

    private Calculation calculateProfile(
            UUID tenantId,
            ProfileCalculationRow profile,
            PeriodView period,
            UUID runId,
            int sequence,
            Instant now) {
        List<CalculationComponent> configured = calculationComponents(tenantId, profile.id());
        BigDecimal base = BigDecimal.ZERO;
        for (CalculationComponent line : configured) {
            if ("BASE".equals(line.componentType())) {
                base = base.add(profileAmount(line, BigDecimal.ZERO));
            }
        }
        UUID payslipId = UUID.randomUUID();
        String payslipNumber =
                "BUL-" + period.code() + "-" + String.format(Locale.ROOT, "%04d", sequence);
        jdbc.update(
                """
                INSERT INTO payroll_payslip(
                    id,tenant_id,payroll_run_id,employee_id,salary_profile_id,payslip_number,
                    currency,gross_amount,deduction_amount,net_amount,status,calculated_at,created_at)
                VALUES(?,?,?,?,?,?,?,0,0,0,'CALCULATED',?,?)
                """,
                payslipId,
                tenantId,
                runId,
                profile.employeeId(),
                profile.id(),
                payslipNumber,
                profile.currency(),
                timestamp(now),
                timestamp(now));

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal deductions = BigDecimal.ZERO;
        for (CalculationComponent line : configured) {
            BigDecimal amount = money(profileAmount(line, base));
            if (amount.signum() == 0) continue;
            insertPayslipLine(
                    tenantId,
                    payslipId,
                    line.componentId(),
                    line.ruleId(),
                    line.componentCode(),
                    line.componentName(),
                    line.componentType(),
                    null,
                    line.rate(),
                    amount,
                    "PROFILE",
                    line.ruleName(),
                    line.displayOrder(),
                    now);
            if (isDeduction(line.componentType())) deductions = deductions.add(amount);
            else gross = gross.add(amount);
        }
        List<AdjustmentCalculationRow> employeeAdjustments =
                calculationAdjustments(tenantId, profile.employeeId(), period.id());
        for (AdjustmentCalculationRow adjustment : employeeAdjustments) {
            BigDecimal amount =
                    adjustment.amount() != null
                            ? adjustment.amount()
                            : adjustment.quantity().multiply(adjustment.unitRate());
            amount = money(amount.abs());
            insertPayslipLine(
                    tenantId,
                    payslipId,
                    adjustment.componentId(),
                    null,
                    adjustment.componentCode(),
                    adjustment.componentName(),
                    adjustment.componentType(),
                    adjustment.quantity(),
                    adjustment.unitRate(),
                    amount,
                    "ADJUSTMENT",
                    adjustment.reason(),
                    adjustment.displayOrder(),
                    now);
            if (isDeduction(adjustment.componentType())) deductions = deductions.add(amount);
            else gross = gross.add(amount);
            jdbc.update(
                    "UPDATE payroll_salary_adjustment SET status='APPLIED',applied_at=? WHERE"
                            + " tenant_id=? AND id=?",
                    timestamp(now),
                    tenantId,
                    adjustment.id());
        }
        BigDecimal net = money(gross.subtract(deductions));
        jdbc.update(
                "UPDATE payroll_payslip SET gross_amount=?,deduction_amount=?,net_amount=?"
                        + " WHERE tenant_id=? AND id=?",
                money(gross),
                money(deductions),
                net,
                tenantId,
                payslipId);
        return new Calculation(money(gross), money(deductions), net);
    }

    private BigDecimal profileAmount(CalculationComponent line, BigDecimal base) {
        return switch (line.calculationBasis()) {
            case "PROFILE_AMOUNT" -> zeroIfNull(line.configuredAmount());
            case "FIXED" ->
                    line.configuredAmount() != null
                            ? line.configuredAmount()
                            : zeroIfNull(line.ruleFixedAmount());
            case "PERCENT_OF_BASE" ->
                    base.multiply(
                                    line.configuredRate() != null
                                            ? line.configuredRate()
                                            : zeroIfNull(line.ruleRatePercent()))
                            .divide(HUNDRED, 6, RoundingMode.HALF_UP);
            case "PER_UNIT" -> BigDecimal.ZERO;
            default -> throw new PayrollValidationException("Base de calcul inconnue");
        };
    }

    private void insertPayslipLine(
            UUID tenantId,
            UUID payslipId,
            UUID componentId,
            UUID ruleId,
            String componentCode,
            String componentName,
            String componentType,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal amount,
            String source,
            String description,
            int displayOrder,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO payroll_payslip_line(
                    id,tenant_id,payslip_id,component_id,rule_id,component_code,component_name,
                    component_type,quantity,rate,amount,source,description,display_order,created_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                UUID.randomUUID(),
                tenantId,
                payslipId,
                componentId,
                ruleId,
                componentCode,
                componentName,
                componentType,
                quantity,
                rate,
                amount,
                source,
                description,
                displayOrder,
                timestamp(now));
    }

    private List<PeriodView> periods(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM payroll_period WHERE tenant_id=? ORDER BY starts_on DESC",
                (result, row) -> period(result),
                tenantId);
    }

    private PeriodView period(UUID tenantId, UUID id) {
        List<PeriodView> result =
                jdbc.query(
                        "SELECT * FROM payroll_period WHERE tenant_id=? AND id=?",
                        (row, number) -> period(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Période de paie introuvable");
        return result.getFirst();
    }

    private static PeriodView period(ResultSet result) throws SQLException {
        return new PeriodView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("label"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getObject("payment_date", LocalDate.class),
                result.getString("currency"),
                result.getString("status"));
    }

    private List<ComponentView> components(UUID tenantId) {
        return jdbc.query(
                "SELECT * FROM payroll_salary_component WHERE tenant_id=? ORDER BY"
                        + " display_order,code",
                (result, row) -> component(result),
                tenantId);
    }

    private ComponentView component(UUID tenantId, UUID id) {
        List<ComponentView> result =
                jdbc.query(
                        "SELECT * FROM payroll_salary_component WHERE tenant_id=? AND id=?",
                        (row, number) -> component(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Composant salarial introuvable");
        return result.getFirst();
    }

    private static ComponentView component(ResultSet result) throws SQLException {
        return new ComponentView(
                result.getObject("id", UUID.class),
                result.getString("code"),
                result.getString("name"),
                result.getString("component_type"),
                result.getString("calculation_basis"),
                result.getString("description"),
                result.getString("expense_account_code"),
                result.getString("liability_account_code"),
                result.getBoolean("active"),
                result.getInt("display_order"));
    }

    private List<RuleView> rules(UUID tenantId) {
        return jdbc.query(
                """
                SELECT r.*,c.code component_code,c.name component_name
                FROM payroll_salary_rule r
                JOIN payroll_salary_component c ON c.id=r.component_id AND c.tenant_id=r.tenant_id
                WHERE r.tenant_id=? ORDER BY r.priority,r.code
                """,
                (result, row) -> rule(result),
                tenantId);
    }

    private RuleView rule(UUID tenantId, UUID id) {
        List<RuleView> result =
                jdbc.query(
                        """
                        SELECT r.*,c.code component_code,c.name component_name
                        FROM payroll_salary_rule r
                        JOIN payroll_salary_component c ON c.id=r.component_id
                            AND c.tenant_id=r.tenant_id
                        WHERE r.tenant_id=? AND r.id=?
                        """,
                        (row, number) -> rule(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Règle salariale introuvable");
        return result.getFirst();
    }

    private static RuleView rule(ResultSet result) throws SQLException {
        return new RuleView(
                result.getObject("id", UUID.class),
                result.getObject("component_id", UUID.class),
                result.getString("component_code"),
                result.getString("component_name"),
                result.getString("code"),
                result.getString("name"),
                result.getInt("priority"),
                result.getBigDecimal("fixed_amount"),
                result.getBigDecimal("rate_percent"),
                result.getBigDecimal("unit_rate"),
                result.getString("condition_expression"),
                result.getString("source_reference"),
                result.getBoolean("legally_validated"),
                result.getObject("effective_from", LocalDate.class),
                result.getObject("effective_to", LocalDate.class),
                result.getBoolean("active"));
    }

    private List<SalaryProfileView> profiles(UUID tenantId) {
        return jdbc
                .query(
                        "SELECT id FROM payroll_employee_salary_profile WHERE tenant_id=? ORDER BY"
                                + " effective_from DESC,code",
                        (result, row) -> result.getObject("id", UUID.class),
                        tenantId)
                .stream()
                .map(id -> profile(tenantId, id))
                .toList();
    }

    private SalaryProfileView profile(UUID tenantId, UUID id) {
        List<SalaryProfileHeader> result =
                jdbc.query(
                        """
                        SELECT p.*,CONCAT(pe.first_name,' ',pe.last_name) employee_name,
                            sp.employee_number
                        FROM payroll_employee_salary_profile p
                        JOIN hr_employee e ON e.id=p.employee_id AND e.tenant_id=p.tenant_id
                        JOIN staff_profile sp ON sp.id=e.staff_profile_id AND sp.tenant_id=e.tenant_id
                        JOIN person pe ON pe.id=e.person_id AND pe.tenant_id=e.tenant_id
                        WHERE p.tenant_id=? AND p.id=?
                        """,
                        (row, number) ->
                                new SalaryProfileHeader(
                                        row.getObject("id", UUID.class),
                                        row.getObject("employee_id", UUID.class),
                                        row.getString("employee_name"),
                                        row.getString("employee_number"),
                                        row.getString("code"),
                                        row.getString("currency"),
                                        row.getObject("effective_from", LocalDate.class),
                                        row.getObject("effective_to", LocalDate.class),
                                        row.getBoolean("active"),
                                        row.getString("payment_method"),
                                        row.getString("notes")),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Profil salarial introuvable");
        SalaryProfileHeader header = result.getFirst();
        return new SalaryProfileView(
                header.id(),
                header.employeeId(),
                header.employeeName(),
                header.employeeNumber(),
                header.code(),
                header.currency(),
                header.effectiveFrom(),
                header.effectiveTo(),
                header.active(),
                header.paymentMethod(),
                header.notes(),
                profileComponents(tenantId, id));
    }

    private List<ProfileComponentView> profileComponents(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT pc.id,pc.component_id,pc.rule_id,pc.configured_amount,
                    pc.configured_rate,pc.configured_unit_rate,c.code component_code,
                    c.name component_name,c.component_type,c.calculation_basis,r.name rule_name
                FROM payroll_profile_component pc
                JOIN payroll_salary_component c ON c.id=pc.component_id
                    AND c.tenant_id=pc.tenant_id
                LEFT JOIN payroll_salary_rule r ON r.id=pc.rule_id AND r.tenant_id=pc.tenant_id
                WHERE pc.tenant_id=? AND pc.profile_id=? AND pc.active=TRUE
                ORDER BY c.display_order,c.code
                """,
                (result, row) ->
                        new ProfileComponentView(
                                result.getObject("id", UUID.class),
                                result.getObject("component_id", UUID.class),
                                result.getObject("rule_id", UUID.class),
                                result.getString("component_code"),
                                result.getString("component_name"),
                                result.getString("component_type"),
                                result.getString("calculation_basis"),
                                result.getString("rule_name"),
                                result.getBigDecimal("configured_amount"),
                                result.getBigDecimal("configured_rate"),
                                result.getBigDecimal("configured_unit_rate")),
                tenantId,
                profileId);
    }

    private List<RunView> runs(UUID tenantId) {
        return jdbc.query(
                """
                SELECT r.*,p.code period_code,p.label period_label,p.starts_on,p.ends_on,
                    p.payment_date,p.currency
                FROM payroll_run r JOIN payroll_period p ON p.id=r.period_id
                    AND p.tenant_id=r.tenant_id
                WHERE r.tenant_id=? ORDER BY r.started_at DESC
                """,
                (result, row) -> run(result),
                tenantId);
    }

    private RunView run(UUID tenantId, UUID id) {
        List<RunView> result =
                jdbc.query(
                        """
                        SELECT r.*,p.code period_code,p.label period_label,p.starts_on,p.ends_on,
                            p.payment_date,p.currency
                        FROM payroll_run r JOIN payroll_period p ON p.id=r.period_id
                            AND p.tenant_id=r.tenant_id
                        WHERE r.tenant_id=? AND r.id=?
                        """,
                        (row, number) -> run(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Run de paie introuvable");
        return result.getFirst();
    }

    private static RunView run(ResultSet result) throws SQLException {
        return new RunView(
                result.getObject("id", UUID.class),
                result.getObject("period_id", UUID.class),
                result.getString("period_code"),
                result.getString("period_label"),
                result.getObject("starts_on", LocalDate.class),
                result.getObject("ends_on", LocalDate.class),
                result.getObject("payment_date", LocalDate.class),
                result.getString("currency"),
                result.getString("run_number"),
                result.getString("status"),
                result.getInt("employee_count"),
                money(result.getBigDecimal("total_gross")),
                money(result.getBigDecimal("total_deductions")),
                money(result.getBigDecimal("total_net")),
                instant(result, "started_at"),
                instant(result, "calculated_at"),
                instant(result, "finalized_at"));
    }

    private List<AdjustmentView> adjustments(UUID tenantId, UUID periodId) {
        String suffix = periodId == null ? "" : " AND a.period_id=?";
        Object[] parameters =
                periodId == null ? new Object[] {tenantId} : new Object[] {tenantId, periodId};
        return jdbc.query(
                """
                SELECT a.*,CONCAT(p.first_name,' ',p.last_name) employee_name,
                    c.code component_code,c.name component_name,c.component_type,
                    pr.code period_code
                FROM payroll_salary_adjustment a
                JOIN hr_employee e ON e.id=a.employee_id AND e.tenant_id=a.tenant_id
                JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                JOIN payroll_salary_component c ON c.id=a.component_id
                    AND c.tenant_id=a.tenant_id
                JOIN payroll_period pr ON pr.id=a.period_id AND pr.tenant_id=a.tenant_id
                WHERE a.tenant_id=?
                """
                        + suffix
                        + " ORDER BY a.created_at DESC",
                (result, row) -> adjustment(result),
                parameters);
    }

    private AdjustmentView adjustment(UUID tenantId, UUID id) {
        List<AdjustmentView> result =
                jdbc.query(
                        """
                        SELECT a.*,CONCAT(p.first_name,' ',p.last_name) employee_name,
                            c.code component_code,c.name component_name,c.component_type,
                            pr.code period_code
                        FROM payroll_salary_adjustment a
                        JOIN hr_employee e ON e.id=a.employee_id AND e.tenant_id=a.tenant_id
                        JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                        JOIN payroll_salary_component c ON c.id=a.component_id
                            AND c.tenant_id=a.tenant_id
                        JOIN payroll_period pr ON pr.id=a.period_id AND pr.tenant_id=a.tenant_id
                        WHERE a.tenant_id=? AND a.id=?
                        """,
                        (row, number) -> adjustment(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Ajustement introuvable");
        return result.getFirst();
    }

    private static AdjustmentView adjustment(ResultSet result) throws SQLException {
        return new AdjustmentView(
                result.getObject("id", UUID.class),
                result.getObject("employee_id", UUID.class),
                result.getString("employee_name"),
                result.getObject("period_id", UUID.class),
                result.getString("period_code"),
                result.getObject("component_id", UUID.class),
                result.getString("component_code"),
                result.getString("component_name"),
                result.getString("component_type"),
                result.getBigDecimal("quantity"),
                result.getBigDecimal("unit_rate"),
                result.getBigDecimal("amount"),
                result.getString("reason"),
                result.getString("status"),
                instant(result, "created_at"),
                instant(result, "applied_at"));
    }

    private List<ProfileCalculationRow> calculationProfiles(UUID tenantId, LocalDate asOf) {
        return jdbc.query(
                """
                SELECT p.id,p.employee_id,p.currency
                FROM payroll_employee_salary_profile p
                JOIN hr_employee e ON e.id=p.employee_id AND e.tenant_id=p.tenant_id
                WHERE p.tenant_id=? AND p.active=TRUE AND e.status='ACTIVE'
                    AND p.effective_from<=? AND (p.effective_to IS NULL OR p.effective_to>=?)
                ORDER BY p.code
                """,
                (result, row) ->
                        new ProfileCalculationRow(
                                result.getObject("id", UUID.class),
                                result.getObject("employee_id", UUID.class),
                                result.getString("currency")),
                tenantId,
                asOf,
                asOf);
    }

    private List<CalculationComponent> calculationComponents(UUID tenantId, UUID profileId) {
        return jdbc.query(
                """
                SELECT c.id component_id,c.code component_code,c.name component_name,
                    c.component_type,c.calculation_basis,c.display_order,pc.rule_id,
                    pc.configured_amount,pc.configured_rate,pc.configured_unit_rate,
                    r.name rule_name,r.fixed_amount rule_fixed_amount,
                    r.rate_percent rule_rate_percent,r.unit_rate rule_unit_rate
                FROM payroll_profile_component pc
                JOIN payroll_salary_component c ON c.id=pc.component_id
                    AND c.tenant_id=pc.tenant_id
                LEFT JOIN payroll_salary_rule r ON r.id=pc.rule_id AND r.tenant_id=pc.tenant_id
                    AND r.active=TRUE
                WHERE pc.tenant_id=? AND pc.profile_id=? AND pc.active=TRUE AND c.active=TRUE
                ORDER BY c.display_order,c.code
                """,
                (result, row) ->
                        new CalculationComponent(
                                result.getObject("component_id", UUID.class),
                                result.getObject("rule_id", UUID.class),
                                result.getString("component_code"),
                                result.getString("component_name"),
                                result.getString("component_type"),
                                result.getString("calculation_basis"),
                                result.getInt("display_order"),
                                result.getString("rule_name"),
                                result.getBigDecimal("configured_amount"),
                                result.getBigDecimal("configured_rate"),
                                result.getBigDecimal("configured_unit_rate"),
                                result.getBigDecimal("rule_fixed_amount"),
                                result.getBigDecimal("rule_rate_percent"),
                                result.getBigDecimal("rule_unit_rate")),
                tenantId,
                profileId);
    }

    private List<AdjustmentCalculationRow> calculationAdjustments(
            UUID tenantId, UUID employeeId, UUID periodId) {
        return jdbc.query(
                """
                SELECT a.id,a.component_id,a.quantity,a.unit_rate,a.amount,a.reason,
                    c.code component_code,c.name component_name,c.component_type,c.display_order
                FROM payroll_salary_adjustment a
                JOIN payroll_salary_component c ON c.id=a.component_id
                    AND c.tenant_id=a.tenant_id
                WHERE a.tenant_id=? AND a.employee_id=? AND a.period_id=?
                    AND a.status='PENDING'
                ORDER BY c.display_order,a.created_at
                """,
                (result, row) ->
                        new AdjustmentCalculationRow(
                                result.getObject("id", UUID.class),
                                result.getObject("component_id", UUID.class),
                                result.getString("component_code"),
                                result.getString("component_name"),
                                result.getString("component_type"),
                                result.getInt("display_order"),
                                result.getBigDecimal("quantity"),
                                result.getBigDecimal("unit_rate"),
                                result.getBigDecimal("amount"),
                                result.getString("reason")),
                tenantId,
                employeeId,
                periodId);
    }

    private List<PayslipSummary> payslips(UUID tenantId, UUID runId) {
        return jdbc.query(
                """
                SELECT ps.*,CONCAT(p.first_name,' ',p.last_name) employee_name,
                    sp.employee_number
                FROM payroll_payslip ps
                JOIN hr_employee e ON e.id=ps.employee_id AND e.tenant_id=ps.tenant_id
                JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                JOIN staff_profile sp ON sp.id=e.staff_profile_id AND sp.tenant_id=e.tenant_id
                WHERE ps.tenant_id=? AND ps.payroll_run_id=? ORDER BY employee_name
                """,
                (result, row) -> payslipSummary(result),
                tenantId,
                runId);
    }

    private PayslipSummary payslipSummary(UUID tenantId, UUID id) {
        List<PayslipSummary> result =
                jdbc.query(
                        """
                        SELECT ps.*,CONCAT(p.first_name,' ',p.last_name) employee_name,
                            sp.employee_number
                        FROM payroll_payslip ps
                        JOIN hr_employee e ON e.id=ps.employee_id AND e.tenant_id=ps.tenant_id
                        JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                        JOIN staff_profile sp ON sp.id=e.staff_profile_id AND sp.tenant_id=e.tenant_id
                        WHERE ps.tenant_id=? AND ps.id=?
                        """,
                        (row, number) -> payslipSummary(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Bulletin de paie introuvable");
        return result.getFirst();
    }

    private static PayslipSummary payslipSummary(ResultSet result) throws SQLException {
        return new PayslipSummary(
                result.getObject("id", UUID.class),
                result.getObject("payroll_run_id", UUID.class),
                result.getObject("employee_id", UUID.class),
                result.getString("employee_name"),
                result.getString("employee_number"),
                result.getString("payslip_number"),
                result.getString("currency"),
                money(result.getBigDecimal("gross_amount")),
                money(result.getBigDecimal("deduction_amount")),
                money(result.getBigDecimal("net_amount")),
                result.getString("status"),
                instant(result, "calculated_at"),
                instant(result, "finalized_at"));
    }

    private List<PayslipLineView> payslipLines(UUID tenantId, UUID payslipId) {
        return jdbc.query(
                """
                SELECT id,component_id,rule_id,component_code,component_name,component_type,
                    quantity,rate,amount,source,description,display_order
                FROM payroll_payslip_line WHERE tenant_id=? AND payslip_id=?
                ORDER BY display_order,component_code,id
                """,
                (result, row) ->
                        new PayslipLineView(
                                result.getObject("id", UUID.class),
                                result.getObject("component_id", UUID.class),
                                result.getObject("rule_id", UUID.class),
                                result.getString("component_code"),
                                result.getString("component_name"),
                                result.getString("component_type"),
                                result.getBigDecimal("quantity"),
                                result.getBigDecimal("rate"),
                                money(result.getBigDecimal("amount")),
                                result.getString("source"),
                                result.getString("description")),
                tenantId,
                payslipId);
    }

    private List<ExportLine> exportLines(UUID tenantId, UUID runId) {
        return jdbc.query(
                """
                SELECT ps.payslip_number,ps.currency,sp.employee_number,
                    CONCAT(p.first_name,' ',p.last_name) employee_name,l.component_code,
                    l.component_name,l.component_type,l.amount,c.expense_account_code,
                    c.liability_account_code
                FROM payroll_payslip_line l
                JOIN payroll_payslip ps ON ps.id=l.payslip_id AND ps.tenant_id=l.tenant_id
                JOIN hr_employee e ON e.id=ps.employee_id AND e.tenant_id=ps.tenant_id
                JOIN staff_profile sp ON sp.id=e.staff_profile_id AND sp.tenant_id=e.tenant_id
                JOIN person p ON p.id=e.person_id AND p.tenant_id=e.tenant_id
                JOIN payroll_salary_component c ON c.id=l.component_id
                    AND c.tenant_id=l.tenant_id
                WHERE l.tenant_id=? AND ps.payroll_run_id=?
                ORDER BY ps.payslip_number,l.display_order,l.component_code
                """,
                (result, row) ->
                        new ExportLine(
                                result.getString("payslip_number"),
                                result.getString("employee_number"),
                                result.getString("employee_name"),
                                result.getString("component_code"),
                                result.getString("component_name"),
                                result.getString("component_type"),
                                result.getString("expense_account_code"),
                                result.getString("liability_account_code"),
                                money(result.getBigDecimal("amount")),
                                result.getString("currency")),
                tenantId,
                runId);
    }

    private AccountingExportView accountingExport(UUID tenantId, UUID id) {
        List<AccountingExportView> result =
                jdbc.query(
                        """
                        SELECT id,export_reference,format,content_text,sha256,created_at
                        FROM payroll_accounting_export WHERE tenant_id=? AND id=?
                        """,
                        (row, number) -> accountingExport(row),
                        tenantId,
                        id);
        if (result.isEmpty()) throw new PayrollNotFoundException("Export comptable introuvable");
        return result.getFirst();
    }

    private static AccountingExportView accountingExport(ResultSet result) throws SQLException {
        return new AccountingExportView(
                result.getObject("id", UUID.class),
                result.getString("export_reference"),
                result.getString("format"),
                result.getString("content_text"),
                result.getString("sha256"),
                instant(result, "created_at"),
                COMPLIANCE_NOTICE);
    }

    private String employeeName(UUID tenantId, UUID employeeId) {
        List<String> result =
                jdbc.query(
                        """
                        SELECT CONCAT(p.first_name,' ',p.last_name) display_name
                        FROM hr_employee e JOIN person p ON p.id=e.person_id
                            AND p.tenant_id=e.tenant_id
                        WHERE e.tenant_id=? AND e.id=?
                        """,
                        (row, number) -> row.getString("display_name"),
                        tenantId,
                        employeeId);
        if (result.isEmpty()) throw new PayrollNotFoundException("Employé introuvable");
        return result.getFirst();
    }

    private static String csv(List<ExportLine> rows) {
        StringBuilder result =
                new StringBuilder(
                        "bulletin;matricule;employe;composant;type;compte_debit;compte_credit;debit;credit;devise\n");
        for (ExportLine row : rows) {
            boolean deduction = isDeduction(row.componentType());
            String debitAccount =
                    deduction
                            ? defaultAccount(row.expenseAccount(), "PAYROLL_PAYABLE")
                            : defaultAccount(row.expenseAccount(), "PAYROLL_EXPENSE");
            String creditAccount = defaultAccount(row.liabilityAccount(), "PAYROLL_PAYABLE");
            result.append(csvValue(row.payslipNumber()))
                    .append(';')
                    .append(csvValue(row.employeeNumber()))
                    .append(';')
                    .append(csvValue(row.employeeName()))
                    .append(';')
                    .append(csvValue(row.componentCode() + " - " + row.componentName()))
                    .append(';')
                    .append(row.componentType())
                    .append(';')
                    .append(csvValue(debitAccount))
                    .append(';')
                    .append(csvValue(creditAccount))
                    .append(';')
                    .append(row.amount().toPlainString())
                    .append(';')
                    .append(row.amount().toPlainString())
                    .append(';')
                    .append(row.currency())
                    .append('\n');
        }
        return result.toString();
    }

    private static String json(List<ExportLine> rows) {
        StringBuilder result =
                new StringBuilder("{\"complianceNotice\":\"")
                        .append(jsonEscape(COMPLIANCE_NOTICE))
                        .append("\",\"entries\":[");
        for (int index = 0; index < rows.size(); index++) {
            if (index > 0) result.append(',');
            ExportLine row = rows.get(index);
            result.append("{\"payslip\":\"")
                    .append(jsonEscape(row.payslipNumber()))
                    .append("\",\"employeeNumber\":\"")
                    .append(jsonEscape(row.employeeNumber()))
                    .append("\",\"employeeName\":\"")
                    .append(jsonEscape(row.employeeName()))
                    .append("\",\"component\":\"")
                    .append(jsonEscape(row.componentCode()))
                    .append("\",\"type\":\"")
                    .append(row.componentType())
                    .append("\",\"amount\":")
                    .append(row.amount().toPlainString())
                    .append(",\"currency\":\"")
                    .append(row.currency())
                    .append("\"}");
        }
        return result.append("]}").toString();
    }

    private static String csvValue(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private static String jsonEscape(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static void validateRuleValues(String basis, RuleCommand command) {
        switch (basis) {
            case "PROFILE_AMOUNT" -> {
                // Le montant est porté par chaque profil salarié.
            }
            case "FIXED" -> {
                if (command.fixedAmount() == null) {
                    throw new PayrollValidationException("Montant fixe requis");
                }
            }
            case "PERCENT_OF_BASE" -> {
                if (command.ratePercent() == null) {
                    throw new PayrollValidationException("Taux requis");
                }
            }
            case "PER_UNIT" -> {
                if (command.unitRate() == null) {
                    throw new PayrollValidationException("Tarif unitaire requis");
                }
            }
            default -> throw new PayrollValidationException("Base de calcul invalide");
        }
    }

    private void audit(
            SchoolPrincipal actor,
            String action,
            String resourceType,
            UUID resourceId,
            String ipAddress) {
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                action,
                resourceType,
                resourceId.toString(),
                ipAddress,
                Map.of(),
                null,
                null);
    }

    private static void require(SchoolPrincipal actor, String permission) {
        if (actor == null || !actor.permissions().contains(permission)) {
            throw new AccessDeniedException("Permission requise : " + permission);
        }
    }

    private static String enumValue(String value, Set<String> allowed, String message) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new PayrollValidationException(message);
        return normalized;
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) throw new PayrollValidationException(message);
        return value.strip();
    }

    private static String nullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String code(String value) {
        return required(value, "Code requis")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "-");
    }

    private static String currency(String value) {
        String normalized = required(value, "Devise requise").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new PayrollValidationException("Devise ISO à trois lettres requise");
        }
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String message) {
        if (value != null && value.signum() < 0) throw new PayrollValidationException(message);
        return value;
    }

    private static BigDecimal moneyOrNull(BigDecimal value) {
        return value == null ? null : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return zeroIfNull(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isDeduction(String componentType) {
        return "DEDUCTION".equals(componentType) || "ABSENCE".equals(componentType);
    }

    private static String defaultAccount(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Dashboard(
            Instant generatedAt,
            String complianceNotice,
            List<PeriodView> periods,
            List<ComponentView> components,
            List<RuleView> rules,
            List<SalaryProfileView> profiles,
            List<RunView> runs,
            List<AdjustmentView> adjustments) {}

    public record PeriodCommand(
            String code,
            String label,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate paymentDate,
            String currency,
            String status) {}

    public record PeriodView(
            UUID id,
            String code,
            String label,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate paymentDate,
            String currency,
            String status) {}

    public record ComponentCommand(
            String code,
            String name,
            String componentType,
            String calculationBasis,
            String description,
            String expenseAccountCode,
            String liabilityAccountCode,
            Integer displayOrder) {}

    public record ComponentView(
            UUID id,
            String code,
            String name,
            String componentType,
            String calculationBasis,
            String description,
            String expenseAccountCode,
            String liabilityAccountCode,
            boolean active,
            int displayOrder) {}

    public record RuleCommand(
            UUID componentId,
            String code,
            String name,
            Integer priority,
            BigDecimal fixedAmount,
            BigDecimal ratePercent,
            BigDecimal unitRate,
            String conditionExpression,
            String sourceReference,
            boolean legallyValidated,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {}

    public record RuleView(
            UUID id,
            UUID componentId,
            String componentCode,
            String componentName,
            String code,
            String name,
            int priority,
            BigDecimal fixedAmount,
            BigDecimal ratePercent,
            BigDecimal unitRate,
            String conditionExpression,
            String sourceReference,
            boolean legallyValidated,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active) {}

    public record ProfileCommand(
            UUID employeeId,
            String code,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String paymentMethod,
            String notes,
            List<ProfileComponentCommand> components) {}

    public record ProfileComponentCommand(
            UUID componentId,
            UUID ruleId,
            BigDecimal configuredAmount,
            BigDecimal configuredRate,
            BigDecimal configuredUnitRate) {}

    public record ProfileComponentView(
            UUID id,
            UUID componentId,
            UUID ruleId,
            String componentCode,
            String componentName,
            String componentType,
            String calculationBasis,
            String ruleName,
            BigDecimal configuredAmount,
            BigDecimal configuredRate,
            BigDecimal configuredUnitRate) {}

    public record SalaryProfileView(
            UUID id,
            UUID employeeId,
            String employeeName,
            String employeeNumber,
            String code,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            String paymentMethod,
            String notes,
            List<ProfileComponentView> components) {}

    public record AdjustmentCommand(
            UUID employeeId,
            UUID periodId,
            UUID componentId,
            BigDecimal quantity,
            BigDecimal unitRate,
            BigDecimal amount,
            String reason) {}

    public record AdjustmentView(
            UUID id,
            UUID employeeId,
            String employeeName,
            UUID periodId,
            String periodCode,
            UUID componentId,
            String componentCode,
            String componentName,
            String componentType,
            BigDecimal quantity,
            BigDecimal unitRate,
            BigDecimal amount,
            String reason,
            String status,
            Instant createdAt,
            Instant appliedAt) {}

    public record RunCommand(UUID periodId, String runNumber) {}

    public record RunView(
            UUID id,
            UUID periodId,
            String periodCode,
            String periodLabel,
            LocalDate startsOn,
            LocalDate endsOn,
            LocalDate paymentDate,
            String currency,
            String runNumber,
            String status,
            int employeeCount,
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNet,
            Instant startedAt,
            Instant calculatedAt,
            Instant finalizedAt) {}

    public record PayslipSummary(
            UUID id,
            UUID payrollRunId,
            UUID employeeId,
            String employeeName,
            String employeeNumber,
            String payslipNumber,
            String currency,
            BigDecimal grossAmount,
            BigDecimal deductionAmount,
            BigDecimal netAmount,
            String status,
            Instant calculatedAt,
            Instant finalizedAt) {}

    public record PayslipLineView(
            UUID id,
            UUID componentId,
            UUID ruleId,
            String componentCode,
            String componentName,
            String componentType,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal amount,
            String source,
            String description) {}

    public record PayslipRecord(
            PayslipSummary payslip, List<PayslipLineView> lines, String complianceNotice) {}

    public record AccountingExportView(
            UUID id,
            String exportReference,
            String format,
            String content,
            String sha256,
            Instant createdAt,
            String complianceNotice) {}

    private record SalaryProfileHeader(
            UUID id,
            UUID employeeId,
            String employeeName,
            String employeeNumber,
            String code,
            String currency,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active,
            String paymentMethod,
            String notes) {}

    private record ProfileCalculationRow(UUID id, UUID employeeId, String currency) {}

    private record CalculationComponent(
            UUID componentId,
            UUID ruleId,
            String componentCode,
            String componentName,
            String componentType,
            String calculationBasis,
            int displayOrder,
            String ruleName,
            BigDecimal configuredAmount,
            BigDecimal configuredRate,
            BigDecimal configuredUnitRate,
            BigDecimal ruleFixedAmount,
            BigDecimal ruleRatePercent,
            BigDecimal ruleUnitRate) {
        BigDecimal rate() {
            if (configuredRate != null) return configuredRate;
            if (ruleRatePercent != null) return ruleRatePercent;
            if (configuredUnitRate != null) return configuredUnitRate;
            return ruleUnitRate;
        }
    }

    private record AdjustmentCalculationRow(
            UUID id,
            UUID componentId,
            String componentCode,
            String componentName,
            String componentType,
            int displayOrder,
            BigDecimal quantity,
            BigDecimal unitRate,
            BigDecimal amount,
            String reason) {}

    private record Calculation(BigDecimal gross, BigDecimal deductions, BigDecimal net) {}

    private record ExportLine(
            String payslipNumber,
            String employeeNumber,
            String employeeName,
            String componentCode,
            String componentName,
            String componentType,
            String expenseAccount,
            String liabilityAccount,
            BigDecimal amount,
            String currency) {}
}
