package ma.schoolz.erp.humanresources.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.humanresources.application.HumanResourcesService;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.AttendanceCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.AttendanceView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.ContractCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.ContractView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.Dashboard;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.DepartmentCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.DepartmentView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.EmployeeCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.EmployeeRecord;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.LeaveDecisionCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.LeaveRequestCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.LeaveRequestView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.PositionCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.PositionView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.QualificationCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.QualificationView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.StaffDocumentCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.StaffDocumentView;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.WorkScheduleCommand;
import ma.schoolz.erp.humanresources.application.HumanResourcesService.WorkScheduleView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/hr")
public class HumanResourcesController {
    private final HumanResourcesService hr;

    public HumanResourcesController(HumanResourcesService hr) {
        this.hr = hr;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate date,
            HttpServletRequest request) {
        return hr.dashboard(actor, date, request.getRemoteAddr());
    }

    @GetMapping("/employees/{employeeId}")
    public EmployeeRecord employee(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID employeeId,
            HttpServletRequest request) {
        return hr.employee(actor, employeeId, request.getRemoteAddr());
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentView createDepartment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody DepartmentCommand command,
            HttpServletRequest request) {
        return hr.createDepartment(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    public PositionView createPosition(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PositionCommand command,
            HttpServletRequest request) {
        return hr.createPosition(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeRecord createEmployee(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody EmployeeCommand command,
            HttpServletRequest request) {
        return hr.createEmployee(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/employees/{employeeId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractView addContract(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID employeeId,
            @RequestBody ContractCommand command,
            HttpServletRequest request) {
        return hr.addContract(actor, employeeId, command, request.getRemoteAddr());
    }

    @PostMapping("/employees/{employeeId}/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkScheduleView addSchedule(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID employeeId,
            @RequestBody WorkScheduleCommand command,
            HttpServletRequest request) {
        return hr.addSchedule(actor, employeeId, command, request.getRemoteAddr());
    }

    @PostMapping("/leave-requests")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveRequestView requestLeave(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody LeaveRequestCommand command,
            HttpServletRequest request) {
        return hr.requestLeave(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/leave-requests/{leaveRequestId}/manager-review")
    public LeaveRequestView managerReview(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID leaveRequestId,
            HttpServletRequest request) {
        return hr.sendToManagerReview(actor, leaveRequestId, request.getRemoteAddr());
    }

    @PostMapping("/leave-requests/{leaveRequestId}/decision")
    public LeaveRequestView decideLeave(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID leaveRequestId,
            @RequestBody LeaveDecisionCommand command,
            HttpServletRequest request) {
        return hr.decideLeave(actor, leaveRequestId, command, request.getRemoteAddr());
    }

    @PostMapping("/attendance")
    public AttendanceView recordAttendance(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AttendanceCommand command,
            HttpServletRequest request) {
        return hr.recordAttendance(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/employees/{employeeId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public StaffDocumentView addDocument(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID employeeId,
            @RequestBody StaffDocumentCommand command,
            HttpServletRequest request) {
        return hr.addDocument(actor, employeeId, command, request.getRemoteAddr());
    }

    @PostMapping("/employees/{employeeId}/qualifications")
    @ResponseStatus(HttpStatus.CREATED)
    public QualificationView addQualification(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID employeeId,
            @RequestBody QualificationCommand command,
            HttpServletRequest request) {
        return hr.addQualification(actor, employeeId, command, request.getRemoteAddr());
    }
}
