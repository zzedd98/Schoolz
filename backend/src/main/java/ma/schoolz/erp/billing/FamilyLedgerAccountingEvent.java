package ma.schoolz.erp.billing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Stable accounting event emitted by the family ledger after a business transaction is validated.
 * The accounting module may consume it when its optional automatic posting policy is enabled.
 */
public record FamilyLedgerAccountingEvent(
        UUID tenantId,
        UUID actorId,
        String eventType,
        String sourceId,
        LocalDate accountingDate,
        BigDecimal amount,
        String currency,
        String reference,
        String description) {}
