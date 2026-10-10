package ma.schoolz.erp.integrations.application;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExcelAdapter implements IntegrationAdapter {
    @Override
    public AdapterDescriptor descriptor() {
        return new AdapterDescriptor(
                "excel",
                "Excel & CSV générique",
                "OFFICE",
                "READY",
                "LOCAL_ADAPTER",
                List.of("CSV_IMPORT", "CSV_EXPORT", "XLSX_IMPORT", "XLSX_EXPORT"),
                "Les formules et macros sont refusées ; seules les valeurs tabulaires sont"
                        + " traitées.");
    }

    @Override
    public List<DatasetSchema> schemas() {
        return new MassarAdapter().schemas();
    }
}
