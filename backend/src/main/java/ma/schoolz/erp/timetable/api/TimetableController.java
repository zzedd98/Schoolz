package ma.schoolz.erp.timetable.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.timetable.application.TimetableService;
import ma.schoolz.erp.timetable.application.TimetableService.ApplyOptimizationCommand;
import ma.schoolz.erp.timetable.application.TimetableService.AssignmentCommand;
import ma.schoolz.erp.timetable.application.TimetableService.AssignmentView;
import ma.schoolz.erp.timetable.application.TimetableService.AvailabilityCommand;
import ma.schoolz.erp.timetable.application.TimetableService.AvailabilityView;
import ma.schoolz.erp.timetable.application.TimetableService.Board;
import ma.schoolz.erp.timetable.application.TimetableService.ConstraintCommand;
import ma.schoolz.erp.timetable.application.TimetableService.ConstraintView;
import ma.schoolz.erp.timetable.application.TimetableService.LessonView;
import ma.schoolz.erp.timetable.application.TimetableService.MoveCommand;
import ma.schoolz.erp.timetable.application.TimetableService.OptimizationView;
import ma.schoolz.erp.timetable.application.TimetableService.PlacementCommand;
import ma.schoolz.erp.timetable.application.TimetableService.ReferenceData;
import ma.schoolz.erp.timetable.application.TimetableService.RoomEquipmentCommand;
import ma.schoolz.erp.timetable.application.TimetableService.RoomView;
import ma.schoolz.erp.timetable.application.TimetableService.ScheduleCommand;
import ma.schoolz.erp.timetable.application.TimetableService.ScheduleSummary;
import ma.schoolz.erp.timetable.application.TimetableService.SubjectCommand;
import ma.schoolz.erp.timetable.application.TimetableService.SubjectView;
import ma.schoolz.erp.timetable.application.TimetableService.ValidationView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/timetable")
public class TimetableController {
    private final TimetableService timetable;

    public TimetableController(TimetableService timetable) {
        this.timetable = timetable;
    }

    @GetMapping("/references")
    public ReferenceData references(@AuthenticationPrincipal SchoolPrincipal actor) {
        return timetable.references(actor);
    }

    @GetMapping("/subjects")
    public List<SubjectView> subjects(@AuthenticationPrincipal SchoolPrincipal actor) {
        return timetable.subjects(actor);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectView subject(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody SubjectCommand command,
            HttpServletRequest request) {
        return timetable.createSubject(actor, command, request.getRemoteAddr());
    }

    @GetMapping("/schedules")
    public List<ScheduleSummary> schedules(@AuthenticationPrincipal SchoolPrincipal actor) {
        return timetable.schedules(actor);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public Board schedule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ScheduleCommand command,
            HttpServletRequest request) {
        return timetable.createSchedule(actor, command, request.getRemoteAddr());
    }

    @GetMapping("/schedules/{scheduleId}")
    public Board board(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID scheduleId) {
        return timetable.board(actor, scheduleId);
    }

    @PostMapping("/rooms/{roomId}/equipment")
    public RoomView equipment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID roomId,
            @RequestBody RoomEquipmentCommand command,
            HttpServletRequest request) {
        return timetable.addEquipment(actor, roomId, command, request.getRemoteAddr());
    }

    @PostMapping("/schedules/{scheduleId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentView assignment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody AssignmentCommand command,
            HttpServletRequest request) {
        return timetable.createAssignment(actor, scheduleId, command, request.getRemoteAddr());
    }

    @PutMapping("/schedules/{scheduleId}/teachers/{teacherId}/slots/{slotId}/availability")
    public AvailabilityView teacherAvailability(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @PathVariable UUID teacherId,
            @PathVariable UUID slotId,
            @RequestBody AvailabilityCommand command,
            HttpServletRequest request) {
        return timetable.teacherAvailability(
                actor, scheduleId, teacherId, slotId, command, request.getRemoteAddr());
    }

    @PutMapping("/schedules/{scheduleId}/rooms/{roomId}/slots/{slotId}/availability")
    public AvailabilityView roomAvailability(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @PathVariable UUID roomId,
            @PathVariable UUID slotId,
            @RequestBody AvailabilityCommand command,
            HttpServletRequest request) {
        return timetable.roomAvailability(
                actor, scheduleId, roomId, slotId, command, request.getRemoteAddr());
    }

    @PostMapping("/schedules/{scheduleId}/constraints")
    @ResponseStatus(HttpStatus.CREATED)
    public ConstraintView constraint(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody ConstraintCommand command,
            HttpServletRequest request) {
        return timetable.createConstraint(actor, scheduleId, command, request.getRemoteAddr());
    }

    @PostMapping("/schedules/{scheduleId}/lessons/validate")
    public ValidationView validate(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody PlacementCommand command) {
        return timetable.validatePlacement(actor, scheduleId, command);
    }

    @PostMapping("/schedules/{scheduleId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonView lesson(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody PlacementCommand command,
            HttpServletRequest request) {
        return timetable.createLesson(actor, scheduleId, command, request.getRemoteAddr());
    }

    @PatchMapping("/schedules/{scheduleId}/lessons/{lessonId}")
    public LessonView move(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @PathVariable UUID lessonId,
            @RequestBody MoveCommand command,
            HttpServletRequest request) {
        return timetable.moveLesson(actor, scheduleId, lessonId, command, request.getRemoteAddr());
    }

    @DeleteMapping("/schedules/{scheduleId}/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @PathVariable UUID lessonId,
            HttpServletRequest request) {
        timetable.deleteLesson(actor, scheduleId, lessonId, request.getRemoteAddr());
    }

    @PostMapping("/schedules/{scheduleId}/optimizer/proposals")
    public OptimizationView optimize(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID scheduleId) {
        return timetable.optimize(actor, scheduleId);
    }

    @PostMapping("/schedules/{scheduleId}/optimizer/apply")
    public Board applyOptimization(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            @RequestBody ApplyOptimizationCommand command,
            HttpServletRequest request) {
        return timetable.applyOptimization(actor, scheduleId, command, request.getRemoteAddr());
    }

    @PostMapping("/schedules/{scheduleId}/publish")
    public Board publish(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID scheduleId,
            HttpServletRequest request) {
        return timetable.publish(actor, scheduleId, request.getRemoteAddr());
    }
}
