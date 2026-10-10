package ma.schoolz.erp.inventory.application;

public class InventoryConflictException extends RuntimeException {
    public InventoryConflictException(String message) {
        super(message);
    }
}
