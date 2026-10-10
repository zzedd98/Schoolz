package ma.schoolz.erp.accounting.application;

public class AccountingNotFoundException extends RuntimeException {
    public AccountingNotFoundException(String message) {
        super(message);
    }
}
