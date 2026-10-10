package ma.schoolz.erp.timetable.application;

public class TimetableNotFoundException extends RuntimeException {
    public TimetableNotFoundException(String message) {
        super(message);
    }
}
