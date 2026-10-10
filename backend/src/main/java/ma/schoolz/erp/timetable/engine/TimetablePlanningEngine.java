package ma.schoolz.erp.timetable.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TimetablePlanningEngine {

    public List<Conflict> conflicts(PlanningSnapshot snapshot, Placement candidate) {
        List<Conflict> conflicts = new ArrayList<>();
        Assignment assignment = snapshot.assignments().get(candidate.assignmentId());
        Slot slot = snapshot.slots().get(candidate.slotId());
        Room room = snapshot.rooms().get(candidate.roomId());
        if (assignment == null || slot == null || room == null) {
            return List.of(
                    new Conflict(
                            "INVALID_REFERENCE",
                            "HARD",
                            "L'affectation, le créneau ou la salle est introuvable"));
        }

        snapshot.placements().stream()
                .filter(existing -> !existing.id().equals(candidate.id()))
                .filter(existing -> existing.slotId().equals(candidate.slotId()))
                .forEach(
                        existing -> {
                            if (existing.teacherId().equals(assignment.teacherId())) {
                                add(
                                        conflicts,
                                        "TEACHER_BUSY",
                                        "HARD",
                                        "L'enseignant est déjà occupé sur ce créneau");
                            }
                            if (existing.roomId().equals(candidate.roomId())) {
                                add(
                                        conflicts,
                                        "ROOM_BUSY",
                                        "HARD",
                                        "La salle est déjà occupée sur ce créneau");
                            }
                            if (existing.classroomId().equals(assignment.classroomId())) {
                                add(
                                        conflicts,
                                        "CLASSROOM_BUSY",
                                        "HARD",
                                        "La classe est déjà occupée sur ce créneau");
                            }
                        });

        String teacherAvailability =
                snapshot.teacherAvailability()
                        .getOrDefault(assignment.teacherId(), Map.of())
                        .get(candidate.slotId());
        availabilityConflict(conflicts, teacherAvailability, "TEACHER");
        String roomAvailability =
                snapshot.roomAvailability()
                        .getOrDefault(candidate.roomId(), Map.of())
                        .get(candidate.slotId());
        availabilityConflict(conflicts, roomAvailability, "ROOM");

        if (room.capacity() < assignment.classroomCapacity()) {
            add(
                    conflicts,
                    "ROOM_CAPACITY",
                    "HARD",
                    "La capacité de la salle est inférieure à celle de la classe");
        }
        if (assignment.requiredRoomType() != null
                && !assignment.requiredRoomType().isBlank()
                && !assignment.requiredRoomType().equalsIgnoreCase(room.roomType())) {
            add(
                    conflicts,
                    "ROOM_TYPE",
                    "HARD",
                    "Le type de salle ne répond pas au besoin de la matière");
        }
        Set<String> missingEquipment = new HashSet<>(assignment.requiredEquipment());
        missingEquipment.removeAll(room.equipment());
        if (!missingEquipment.isEmpty()) {
            add(
                    conflicts,
                    "ROOM_EQUIPMENT",
                    "HARD",
                    "Équipement manquant : " + String.join(", ", missingEquipment));
        }

        long weeklyCount =
                snapshot.placements().stream()
                        .filter(existing -> !existing.id().equals(candidate.id()))
                        .filter(existing -> existing.assignmentId().equals(assignment.id()))
                        .count();
        if (weeklyCount >= assignment.weeklyPeriods()) {
            add(
                    conflicts,
                    "SUBJECT_WEEKLY_VOLUME",
                    snapshot.weeklyVolumeHard() ? "HARD" : "SOFT",
                    "Le volume hebdomadaire prévu est déjà atteint");
        }

        List<Placement> sameTeacherDay =
                snapshot.placements().stream()
                        .filter(existing -> !existing.id().equals(candidate.id()))
                        .filter(existing -> existing.teacherId().equals(assignment.teacherId()))
                        .filter(existing -> sameDay(snapshot, existing.slotId(), slot.dayOfWeek()))
                        .toList();
        int maxDaily = snapshot.maxDailyPeriods().getOrDefault(assignment.teacherId(), 8);
        if (sameTeacherDay.size() + 1 > maxDaily) {
            add(
                    conflicts,
                    "TEACHER_MAX_DAILY",
                    snapshot.maxDailyHard() ? "HARD" : "SOFT",
                    "Le maximum quotidien de l'enseignant serait dépassé");
        }
        int maxConsecutive =
                snapshot.maxConsecutivePeriods().getOrDefault(assignment.teacherId(), 4);
        List<Integer> sequences = new ArrayList<>();
        sequences.add(slot.sequence());
        sameTeacherDay.forEach(
                placement -> sequences.add(snapshot.slots().get(placement.slotId()).sequence()));
        if (longestConsecutive(sequences) > maxConsecutive) {
            add(
                    conflicts,
                    "TEACHER_MAX_CONSECUTIVE",
                    snapshot.maxConsecutiveHard() ? "HARD" : "SOFT",
                    "Le maximum de cours consécutifs serait dépassé");
        }
        return conflicts;
    }

    public OptimizationResult optimize(PlanningSnapshot snapshot) {
        List<Placement> working = new ArrayList<>(snapshot.placements());
        List<Placement> proposals = new ArrayList<>();
        Map<UUID, Integer> unscheduled = new LinkedHashMap<>();
        List<Assignment> assignments =
                snapshot.assignments().values().stream()
                        .sorted(
                                Comparator.comparingInt(
                                                (Assignment value) ->
                                                        value.requiredEquipment().size())
                                        .reversed()
                                        .thenComparing(Assignment::id))
                        .toList();
        int score = 0;
        for (Assignment assignment : assignments) {
            long current =
                    working.stream()
                            .filter(value -> value.assignmentId().equals(assignment.id()))
                            .count();
            int missing = Math.max(0, assignment.weeklyPeriods() - (int) current);
            for (int index = 0; index < missing; index++) {
                CandidateChoice choice = bestChoice(snapshot, working, assignment);
                if (choice == null) {
                    unscheduled.merge(assignment.id(), 1, Integer::sum);
                    continue;
                }
                working.add(choice.placement());
                proposals.add(choice.placement());
                score += choice.score();
            }
        }
        return new OptimizationResult(List.copyOf(proposals), Map.copyOf(unscheduled), score);
    }

    private CandidateChoice bestChoice(
            PlanningSnapshot original, List<Placement> working, Assignment assignment) {
        CandidateChoice best = null;
        for (Slot slot :
                original.slots().values().stream()
                        .sorted(
                                Comparator.comparingInt(Slot::dayOfWeek)
                                        .thenComparingInt(Slot::sequence))
                        .toList()) {
            for (Room room :
                    original.rooms().values().stream()
                            .sorted(Comparator.comparing(Room::name).thenComparing(Room::id))
                            .toList()) {
                Placement candidate =
                        new Placement(
                                UUID.randomUUID(),
                                assignment.id(),
                                assignment.classroomId(),
                                assignment.subjectId(),
                                assignment.teacherId(),
                                room.id(),
                                slot.id());
                PlanningSnapshot current = original.withPlacements(working);
                List<Conflict> conflicts = conflicts(current, candidate);
                if (conflicts.stream().anyMatch(Conflict::hard)) continue;
                int candidateScore =
                        conflicts.stream().mapToInt(value -> value.hard() ? 1000 : 20).sum();
                String teacherPreference =
                        original.teacherAvailability()
                                .getOrDefault(assignment.teacherId(), Map.of())
                                .get(slot.id());
                if ("PREFERRED".equals(teacherPreference)) candidateScore -= 10;
                String roomPreference =
                        original.roomAvailability()
                                .getOrDefault(room.id(), Map.of())
                                .get(slot.id());
                if ("PREFERRED".equals(roomPreference)) candidateScore -= 5;
                CandidateChoice choice = new CandidateChoice(candidate, candidateScore);
                if (best == null || choice.score() < best.score()) best = choice;
            }
        }
        return best;
    }

    private static boolean sameDay(PlanningSnapshot snapshot, UUID slotId, int day) {
        Slot slot = snapshot.slots().get(slotId);
        return slot != null && slot.dayOfWeek() == day;
    }

    private static int longestConsecutive(List<Integer> values) {
        if (values.isEmpty()) return 0;
        List<Integer> sorted = values.stream().distinct().sorted().toList();
        int longest = 1;
        int current = 1;
        for (int index = 1; index < sorted.size(); index++) {
            if (sorted.get(index) == sorted.get(index - 1) + 1) {
                current++;
                longest = Math.max(longest, current);
            } else {
                current = 1;
            }
        }
        return longest;
    }

    private static void availabilityConflict(
            List<Conflict> conflicts, String availability, String target) {
        if ("UNAVAILABLE".equals(availability)) {
            add(
                    conflicts,
                    target + "_UNAVAILABLE",
                    "HARD",
                    target.equals("TEACHER")
                            ? "L'enseignant est indisponible sur ce créneau"
                            : "La salle est indisponible sur ce créneau");
        } else if ("AVOID".equals(availability)) {
            add(
                    conflicts,
                    target + "_AVOID",
                    "SOFT",
                    target.equals("TEACHER")
                            ? "L'enseignant préfère éviter ce créneau"
                            : "Il est préférable d'éviter cette salle sur ce créneau");
        }
    }

    private static void add(
            List<Conflict> conflicts, String code, String severity, String message) {
        if (conflicts.stream().noneMatch(value -> value.code().equals(code))) {
            conflicts.add(new Conflict(code, severity, message));
        }
    }

    public record Slot(UUID id, int dayOfWeek, int sequence) {}

    public record Room(UUID id, String name, String roomType, int capacity, Set<String> equipment) {
        public Room {
            equipment = Set.copyOf(equipment);
        }
    }

    public record Assignment(
            UUID id,
            UUID classroomId,
            UUID subjectId,
            UUID teacherId,
            int classroomCapacity,
            int weeklyPeriods,
            String requiredRoomType,
            Set<String> requiredEquipment) {
        public Assignment {
            requiredEquipment = Set.copyOf(requiredEquipment);
        }
    }

    public record Placement(
            UUID id,
            UUID assignmentId,
            UUID classroomId,
            UUID subjectId,
            UUID teacherId,
            UUID roomId,
            UUID slotId) {}

    public record Conflict(String code, String severity, String message) {
        public boolean hard() {
            return "HARD".equals(severity);
        }
    }

    public record PlanningSnapshot(
            Map<UUID, Slot> slots,
            Map<UUID, Room> rooms,
            Map<UUID, Assignment> assignments,
            List<Placement> placements,
            Map<UUID, Map<UUID, String>> teacherAvailability,
            Map<UUID, Map<UUID, String>> roomAvailability,
            Map<UUID, Integer> maxDailyPeriods,
            Map<UUID, Integer> maxConsecutivePeriods,
            boolean maxDailyHard,
            boolean maxConsecutiveHard,
            boolean weeklyVolumeHard) {
        public PlanningSnapshot {
            slots = Map.copyOf(slots);
            rooms = Map.copyOf(rooms);
            assignments = Map.copyOf(assignments);
            placements = List.copyOf(placements);
            teacherAvailability = deepCopy(teacherAvailability);
            roomAvailability = deepCopy(roomAvailability);
            maxDailyPeriods = Map.copyOf(maxDailyPeriods);
            maxConsecutivePeriods = Map.copyOf(maxConsecutivePeriods);
        }

        public PlanningSnapshot withPlacements(List<Placement> replacement) {
            return new PlanningSnapshot(
                    slots,
                    rooms,
                    assignments,
                    replacement,
                    teacherAvailability,
                    roomAvailability,
                    maxDailyPeriods,
                    maxConsecutivePeriods,
                    maxDailyHard,
                    maxConsecutiveHard,
                    weeklyVolumeHard);
        }

        private static Map<UUID, Map<UUID, String>> deepCopy(Map<UUID, Map<UUID, String>> source) {
            Map<UUID, Map<UUID, String>> result = new HashMap<>();
            source.forEach((key, value) -> result.put(key, Map.copyOf(value)));
            return Map.copyOf(result);
        }
    }

    public record OptimizationResult(
            List<Placement> proposals, Map<UUID, Integer> unscheduled, int score) {}

    private record CandidateChoice(Placement placement, int score) {}
}
