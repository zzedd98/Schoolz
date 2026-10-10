package ma.schoolz.erp.homework.application;

public class HomeworkNotFoundException extends RuntimeException {
    public HomeworkNotFoundException(String message) {
        super(message);
    }
}
