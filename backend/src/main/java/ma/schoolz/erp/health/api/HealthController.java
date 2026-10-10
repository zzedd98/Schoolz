package ma.schoolz.erp.health.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.health.application.HealthService;
import ma.schoolz.erp.health.application.HealthService.AllergyCommand;
import ma.schoolz.erp.health.application.HealthService.AllergyView;
import ma.schoolz.erp.health.application.HealthService.Dashboard;
import ma.schoolz.erp.health.application.HealthService.EmergencyActionCommand;
import ma.schoolz.erp.health.application.HealthService.EmergencyActionView;
import ma.schoolz.erp.health.application.HealthService.HealthPolicyView;
import ma.schoolz.erp.health.application.HealthService.IncidentCommand;
import ma.schoolz.erp.health.application.HealthService.IncidentView;
import ma.schoolz.erp.health.application.HealthService.MedicationCommand;
import ma.schoolz.erp.health.application.HealthService.MedicationView;
import ma.schoolz.erp.health.application.HealthService.NurseVisitView;
import ma.schoolz.erp.health.application.HealthService.PolicyCommand;
import ma.schoolz.erp.health.application.HealthService.ProfileCommand;
import ma.schoolz.erp.health.application.HealthService.StudentHealthRecord;
import ma.schoolz.erp.health.application.HealthService.VaccinationCommand;
import ma.schoolz.erp.health.application.HealthService.VaccinationView;
import ma.schoolz.erp.health.application.HealthService.VisitCommand;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthService health;

    public HealthController(HealthService health) {
        this.health = health;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor, HttpServletRequest request) {
        return health.dashboard(actor, request.getRemoteAddr());
    }

    @GetMapping("/students/{studentId}")
    public StudentHealthRecord student(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            HttpServletRequest request) {
        return health.student(actor, studentId, request.getRemoteAddr());
    }

    @PutMapping("/students/{studentId}/profile")
    public StudentHealthRecord saveProfile(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody ProfileCommand command,
            HttpServletRequest request) {
        return health.saveProfile(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/students/{studentId}/allergies")
    @ResponseStatus(HttpStatus.CREATED)
    public AllergyView addAllergy(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody AllergyCommand command,
            HttpServletRequest request) {
        return health.addAllergy(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/students/{studentId}/medications")
    @ResponseStatus(HttpStatus.CREATED)
    public MedicationView addMedication(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody MedicationCommand command,
            HttpServletRequest request) {
        return health.addMedication(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/students/{studentId}/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView addIncident(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody IncidentCommand command,
            HttpServletRequest request) {
        return health.addIncident(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/students/{studentId}/visits")
    @ResponseStatus(HttpStatus.CREATED)
    public NurseVisitView addVisit(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody VisitCommand command,
            HttpServletRequest request) {
        return health.addVisit(actor, studentId, command, request.getRemoteAddr());
    }

    @PostMapping("/incidents/{incidentId}/emergency-actions")
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyActionView addEmergencyAction(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID incidentId,
            @RequestBody EmergencyActionCommand command,
            HttpServletRequest request) {
        return health.addEmergencyAction(actor, incidentId, command, request.getRemoteAddr());
    }

    @PostMapping("/students/{studentId}/vaccinations")
    @ResponseStatus(HttpStatus.CREATED)
    public VaccinationView addVaccination(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID studentId,
            @RequestBody VaccinationCommand command,
            HttpServletRequest request) {
        return health.addVaccination(actor, studentId, command, request.getRemoteAddr());
    }

    @PutMapping("/policy")
    public HealthPolicyView updatePolicy(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PolicyCommand command,
            HttpServletRequest request) {
        return health.updatePolicy(actor, command, request.getRemoteAddr());
    }
}
