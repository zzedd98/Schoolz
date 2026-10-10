package ma.schoolz.erp.payroll.application;

public class PayrollNotFoundException extends RuntimeException {
    public PayrollNotFoundException(String message) {
        super(message);
    }
}
