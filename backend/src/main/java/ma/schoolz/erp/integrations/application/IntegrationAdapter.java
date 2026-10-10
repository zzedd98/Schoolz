package ma.schoolz.erp.integrations.application;

import java.util.List;

public interface IntegrationAdapter {
    AdapterDescriptor descriptor();

    List<DatasetSchema> schemas();

    default DatasetSchema schema(String dataset) {
        return schemas().stream()
                .filter(candidate -> candidate.dataset().equals(dataset))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("Jeu de données non pris en charge"));
    }

    record AdapterDescriptor(
            String key,
            String name,
            String category,
            String status,
            String transportMode,
            List<String> capabilities,
            String disclaimer) {}

    record DatasetSchema(
            String dataset,
            String label,
            List<String> headers,
            boolean importable,
            boolean exportable) {}
}
