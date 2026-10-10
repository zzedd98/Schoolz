package ma.schoolz.erp.integrations.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MessagingAdapter implements IntegrationAdapter {
    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                "messaging-file",
                "Messagerie par fichiers",
                "MESSAGING",
                "READY",
                "FILE_ONLY",
                List.of("CSV_IMPORT", "CSV_EXPORT", "XLSX_EXPORT"),
                "Les fichiers préparent les destinataires ou relisent des statuts ; aucun message"
                        + " externe n’est envoyé sans fournisseur configuré.");
    }

    @Override
    public List<DatasetSchema> schemas() {
        return List.of(
                new DatasetSchema(
                        "RECIPIENTS",
                        "Destinataires",
                        List.of(
                                "recipient_reference",
                                "display_name",
                                "channel",
                                "destination",
                                "locale"),
                        true,
                        true),
                new DatasetSchema(
                        "DELIVERY_REPORTS",
                        "Rapports de livraison",
                        List.of(
                                "message_reference",
                                "provider_reference",
                                "status",
                                "occurred_at",
                                "detail"),
                        true,
                        true));
    }
}
