package ma.schoolz.erp.integrations.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MassarAdapter implements IntegrationAdapter {
    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                "massar-file",
                "Passerelle Massar par fichiers",
                "SCOLARITY",
                "READY",
                "FILE_ONLY",
                List.of("CSV_IMPORT", "CSV_EXPORT", "XLSX_IMPORT", "XLSX_EXPORT"),
                "Aucun scraping et aucun accès officiel ne sont utilisés. Les modèles SchoolZ ne"
                        + " prétendent pas être un format officiel Massar.");
    }

    @Override
    public List<DatasetSchema> schemas() {
        return List.of(
                new DatasetSchema(
                        "STUDENTS",
                        "Élèves — modèle d’échange SchoolZ",
                        List.of(
                                "student_identifier",
                                "last_name",
                                "first_name",
                                "birth_date",
                                "gender",
                                "class_code",
                                "academic_year"),
                        true,
                        true),
                new DatasetSchema(
                        "GRADES",
                        "Notes — modèle d’échange SchoolZ",
                        List.of(
                                "student_identifier",
                                "subject_code",
                                "assessment",
                                "score",
                                "score_max",
                                "assessment_date"),
                        true,
                        true));
    }
}
