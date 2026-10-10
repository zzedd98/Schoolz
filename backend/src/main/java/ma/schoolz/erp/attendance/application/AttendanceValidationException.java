package ma.schoolz.erp.attendance.application;

public class AttendanceValidationException extends RuntimeException {
    public AttendanceValidationException(String message) {
        super(message);
    }
}
