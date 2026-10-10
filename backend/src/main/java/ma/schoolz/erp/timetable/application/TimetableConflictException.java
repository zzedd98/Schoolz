package ma.schoolz.erp.timetable.application;

import java.util.List;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Conflict;

public class TimetableConflictException extends RuntimeException {
    private final List<Conflict> conflicts;

    public TimetableConflictException(List<Conflict> conflicts) {
        super("Le placement contient un ou plusieurs conflits bloquants");
        this.conflicts = List.copyOf(conflicts);
    }

    public List<Conflict> conflicts() {
        return conflicts;
    }
}
