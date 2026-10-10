package ma.schoolz.erp.integrations.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AccountingAdapter implements IntegrationAdapter {
    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                "accounting-file",
                "Comptabilité par fichiers",
                "ACCOUNTING",
                "READY",
                "FILE_ONLY",
                List.of("CSV_EXPORT", "XLSX_EXPORT"),
                "Format d’échange générique SchoolZ à mapper vers le logiciel comptable choisi par"
                        + " l’établissement.");
    }

    @Override
    public List<DatasetSchema> schemas() {
        return List.of(
                new DatasetSchema(
                        "JOURNAL_ENTRIES",
                        "Écritures comptables",
                        List.of(
                                "entry_number",
                                "entry_date",
                                "journal_code",
                                "account_code",
                                "label",
                                "debit",
                                "credit",
                                "reference"),
                        false,
                        true));
    }
}
