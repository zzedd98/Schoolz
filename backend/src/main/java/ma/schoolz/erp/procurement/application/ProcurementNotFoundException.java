package ma.schoolz.erp.procurement.application;

public class ProcurementNotFoundException extends RuntimeException {
    public ProcurementNotFoundException(String message) {
        super(message);
    }
}
