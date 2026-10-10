package ma.schoolz.erp.grading.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.grading.application.GradingService;
import ma.schoolz.erp.grading.application.GradingService.AssessmentCommand;
import ma.schoolz.erp.grading.application.GradingService.AssessmentTypeCommand;
import ma.schoolz.erp.grading.application.GradingService.AssessmentTypeView;
import ma.schoolz.erp.grading.application.GradingService.AssessmentView;
import ma.schoolz.erp.grading.application.GradingService.CoefficientCommand;
import ma.schoolz.erp.grading.application.GradingService.CoefficientView;
import ma.schoolz.erp.grading.application.GradingService.CompetencyCommand;
import ma.schoolz.erp.grading.application.GradingService.CompetencyEvaluationCommand;
import ma.schoolz.erp.grading.application.GradingService.CompetencyEvaluationView;
import ma.schoolz.erp.grading.application.GradingService.CompetencyView;
import ma.schoolz.erp.grading.application.GradingService.GradeCommand;
import ma.schoolz.erp.grading.application.GradingService.GradeHistoryView;
import ma.schoolz.erp.grading.application.GradingService.GradeScaleCommand;
import ma.schoolz.erp.grading.application.GradingService.GradeScaleView;
import ma.schoolz.erp.grading.application.GradingService.GradeView;
import ma.schoolz.erp.grading.application.GradingService.GradingDashboard;
import ma.schoolz.erp.grading.application.GradingService.PolicyCommand;
import ma.schoolz.erp.grading.application.GradingService.PolicyView;
import ma.schoolz.erp.grading.application.GradingService.ReferenceData;
import ma.schoolz.erp.grading.application.GradingService.ReportCardTemplateCommand;
import ma.schoolz.erp.grading.application.GradingService.ReportCardTemplateView;
import ma.schoolz.erp.grading.application.GradingService.ReportCardView;
import ma.schoolz.erp.grading.application.GradingService.TeacherCommentCommand;
import ma.schoolz.erp.grading.application.GradingService.TeacherCommentView;
import ma.schoolz.erp.grading.application.ReportCardPublicationService;
import ma.schoolz.erp.grading.application.ReportCardPublicationService.PdfDocument;
import ma.schoolz.erp.grading.application.ReportCardPublicationService.PublicationCommand;
import ma.schoolz.erp.grading.application.ReportCardPublicationService.PublicationView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grading")
public class GradingController {
    private final GradingService grading;
    private final ReportCardPublicationService publications;

    public GradingController(GradingService grading, ReportCardPublicationService publications) {
        this.grading = grading;
        this.publications = publications;
    }

    @GetMapping("/references")
    public ReferenceData references(@AuthenticationPrincipal SchoolPrincipal actor) {
        return grading.references(actor);
    }

    @GetMapping("/dashboard")
    public GradingDashboard dashboard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam UUID classroomId,
            @RequestParam UUID academicTermId) {
        return grading.dashboard(actor, classroomId, academicTermId);
    }

    @PostMapping("/scales")
    @ResponseStatus(HttpStatus.CREATED)
    public GradeScaleView scale(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody GradeScaleCommand command,
            HttpServletRequest request) {
        return grading.createScale(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/policies")
    public PolicyView policy(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody PolicyCommand command,
            HttpServletRequest request) {
        return grading.savePolicy(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/assessment-types")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentTypeView assessmentType(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AssessmentTypeCommand command,
            HttpServletRequest request) {
        return grading.createAssessmentType(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/coefficients")
    public CoefficientView coefficient(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CoefficientCommand command,
            HttpServletRequest request) {
        return grading.saveCoefficient(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/competencies")
    @ResponseStatus(HttpStatus.CREATED)
    public CompetencyView competency(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CompetencyCommand command,
            HttpServletRequest request) {
        return grading.createCompetency(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/report-card-templates")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCardTemplateView template(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ReportCardTemplateCommand command,
            HttpServletRequest request) {
        return grading.createTemplate(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssessmentView assessment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody AssessmentCommand command,
            HttpServletRequest request) {
        return grading.createAssessment(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/assessments/{assessmentId}/publish")
    public AssessmentView publishAssessment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID assessmentId,
            HttpServletRequest request) {
        return grading.publishAssessment(actor, assessmentId, request.getRemoteAddr());
    }

    @PutMapping("/assessments/{assessmentId}/grades/{studentId}")
    public GradeView grade(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID assessmentId,
            @PathVariable UUID studentId,
            @RequestBody GradeCommand command,
            HttpServletRequest request) {
        return grading.saveGrade(actor, assessmentId, studentId, command, request.getRemoteAddr());
    }

    @GetMapping("/grades/{gradeId}/history")
    public List<GradeHistoryView> history(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID gradeId) {
        return grading.gradeHistory(actor, gradeId);
    }

    @PostMapping("/competency-evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public CompetencyEvaluationView competencyEvaluation(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CompetencyEvaluationCommand command,
            HttpServletRequest request) {
        return grading.evaluateCompetency(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/teacher-comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherCommentView comment(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody TeacherCommentCommand command,
            HttpServletRequest request) {
        return grading.createComment(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/report-cards/calculate")
    public List<ReportCardView> calculate(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam UUID classroomId,
            @RequestParam UUID academicTermId,
            @RequestParam(required = false) UUID templateId,
            HttpServletRequest request) {
        return grading.calculateReportCards(
                actor, classroomId, academicTermId, templateId, request.getRemoteAddr());
    }

    @PostMapping("/report-cards/{reportCardId}/preview")
    public ResponseEntity<byte[]> previewReportCard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID reportCardId,
            @RequestBody PublicationCommand command) {
        return pdf(publications.preview(actor, reportCardId, command));
    }

    @PostMapping("/report-cards/{reportCardId}/publish")
    public PublicationView publishReportCard(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID reportCardId,
            @RequestBody PublicationCommand command,
            HttpServletRequest request) {
        return publications.publish(actor, reportCardId, command, request.getRemoteAddr());
    }

    @GetMapping("/report-cards/{reportCardId}/pdf")
    public ResponseEntity<byte[]> downloadReportCard(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID reportCardId) {
        return pdf(publications.download(actor, reportCardId));
    }

    @GetMapping("/report-cards/{reportCardId}/versions")
    public List<PublicationView> reportCardVersions(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID reportCardId) {
        return publications.versions(actor, reportCardId);
    }

    private static ResponseEntity<byte[]> pdf(PdfDocument document) {
        ContentDisposition disposition =
                (document.preview() ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(document.fileName())
                        .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.content().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-SHA256", document.sha256())
                .body(document.content());
    }
}
