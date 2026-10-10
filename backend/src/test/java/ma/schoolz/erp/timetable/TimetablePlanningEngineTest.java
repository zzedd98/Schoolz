package ma.schoolz.erp.timetable;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Assignment;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Placement;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.PlanningSnapshot;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Room;
import ma.schoolz.erp.timetable.engine.TimetablePlanningEngine.Slot;
import org.junit.jupiter.api.Test;

class TimetablePlanningEngineTest {
    private final TimetablePlanningEngine engine = new TimetablePlanningEngine();

    @Test
    void detectorReportsTeacherRoomAndClassOccupancyTogether() {
        UUID assignmentId = UUID.randomUUID();
        UUID otherAssignmentId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        Assignment assignment =
                new Assignment(
                        assignmentId, classroomId, subjectId, teacherId, 20, 2, null, Set.of());
        Placement existing =
                new Placement(
                        UUID.randomUUID(),
                        otherAssignmentId,
                        classroomId,
                        UUID.randomUUID(),
                        teacherId,
                        roomId,
                        slotId);
        PlanningSnapshot snapshot =
                snapshot(
                        Map.of(slotId, new Slot(slotId, 1, 1)),
                        Map.of(roomId, new Room(roomId, "Salle", "CLASSROOM", 30, Set.of())),
                        Map.of(assignmentId, assignment),
                        List.of(existing),
                        Map.of());
        Placement candidate =
                new Placement(
                        UUID.randomUUID(),
                        assignmentId,
                        classroomId,
                        subjectId,
                        teacherId,
                        roomId,
                        slotId);

        assertThat(engine.conflicts(snapshot, candidate))
                .extracting(TimetablePlanningEngine.Conflict::code)
                .contains("TEACHER_BUSY", "ROOM_BUSY", "CLASSROOM_BUSY");
    }

    @Test
    void optimizerRespectsDailyTeacherLimitWithoutMutatingTheSnapshot() {
        UUID assignmentId = UUID.randomUUID();
        UUID classroomId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID monday = UUID.randomUUID();
        UUID tuesday = UUID.randomUUID();
        Assignment assignment =
                new Assignment(
                        assignmentId, classroomId, subjectId, teacherId, 20, 2, null, Set.of());
        PlanningSnapshot snapshot =
                snapshot(
                        Map.of(
                                monday, new Slot(monday, 1, 1),
                                tuesday, new Slot(tuesday, 2, 1)),
                        Map.of(roomId, new Room(roomId, "Salle", "CLASSROOM", 30, Set.of())),
                        Map.of(assignmentId, assignment),
                        List.of(),
                        Map.of(teacherId, 1));

        var result = engine.optimize(snapshot);

        assertThat(result.proposals()).hasSize(2);
        assertThat(result.proposals())
                .extracting(Placement::slotId)
                .containsExactly(monday, tuesday);
        assertThat(result.unscheduled()).isEmpty();
        assertThat(snapshot.placements()).isEmpty();
    }

    private static PlanningSnapshot snapshot(
            Map<UUID, Slot> slots,
            Map<UUID, Room> rooms,
            Map<UUID, Assignment> assignments,
            List<Placement> placements,
            Map<UUID, Integer> maxDaily) {
        return new PlanningSnapshot(
                slots,
                rooms,
                assignments,
                placements,
                Map.of(),
                Map.of(),
                maxDaily,
                Map.of(),
                true,
                true,
                true);
    }
}
