package ma.schoolz.erp.inventory.application;

public class InventoryValidationException extends RuntimeException {
    public InventoryValidationException(String message) {
        super(message);
    }
}
