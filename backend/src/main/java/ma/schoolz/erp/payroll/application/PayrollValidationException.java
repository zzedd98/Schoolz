package ma.schoolz.erp.payroll.application;

public class PayrollValidationException extends RuntimeException {
    public PayrollValidationException(String message) {
        super(message);
    }
}
