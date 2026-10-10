package ma.schoolz.erp.timetable.application;

public class TimetableValidationException extends RuntimeException {
    public TimetableValidationException(String message) {
        super(message);
    }
}
