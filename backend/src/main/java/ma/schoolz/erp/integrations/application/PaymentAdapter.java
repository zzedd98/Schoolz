package ma.schoolz.erp.integrations.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PaymentAdapter implements IntegrationAdapter {
    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                "payment-file",
                "Rapprochement paiements",
                "PAYMENT",
                "READY",
                "FILE_ONLY",
                List.of("CSV_IMPORT", "XLSX_IMPORT", "CSV_EXPORT"),
                "Aucune transaction bancaire n’est initiée : l’adaptateur échange des fichiers de"
                        + " rapprochement.");
    }

    @Override
    public List<DatasetSchema> schemas() {
        return List.of(
                new DatasetSchema(
                        "PAYMENTS",
                        "Paiements et rapprochement",
                        List.of(
                                "external_reference",
                                "family_reference",
                                "payment_date",
                                "amount",
                                "currency",
                                "status"),
                        true,
                        true));
    }
}
