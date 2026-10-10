package ma.schoolz.erp.grading.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.CompetencyLine;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.DocumentData;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.GradeLine;
import ma.schoolz.erp.grading.document.ReportCardPdfRenderer.SubjectLine;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportCardPublicationService {
    private static final Set<String> LANGUAGE_MODES = Set.of("FR", "AR", "BILINGUAL");

    private final JdbcTemplate jdbc;
    private final ReportCardPdfRenderer renderer;
    private final IdentityAuditTrail audit;
    private final ObjectMapper json;
    private final Clock clock;

    public ReportCardPublicationService(
            JdbcTemplate jdbc,
            ReportCardPdfRenderer renderer,
            IdentityAuditTrail audit,
            ObjectMapper json,
            Clock clock) {
        this.jdbc = jdbc;
        this.renderer = renderer;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public PdfDocument preview(
            SchoolPrincipal actor, UUID reportCardId, PublicationCommand command) {
        PublicationCommand validated = validate(command, actor.displayName());
        ReportRow report = report(actor.tenantId(), reportCardId);
        if (!"CALCULATED".equals(report.status())) {
            throw new GradingConflictException(
                    "Seul un bulletin calculé peut être prévisualisé avant publication");
        }
        DocumentData data = documentData(actor.tenantId(), report, validated, clock.instant());
        byte[] content = renderer.render(data, true);
        return new PdfDocument(
                content, fileName(report, validated.languageMode(), true), sha256(content), true);
    }

    @Transactional
    @PreAuthorize("hasAuthority('report-card.publish')")
    public PublicationView publish(
            SchoolPrincipal actor,
            UUID reportCardId,
            PublicationCommand command,
            String ipAddress) {
        PublicationCommand validated = validate(command, actor.displayName());
        ReportRow report = report(actor.tenantId(), reportCardId);
        if ("PUBLISHED".equals(report.status())) {
            throw new GradingConflictException("Cette version du bulletin est déjà publiée");
        }
        if (!"CALCULATED".equals(report.status())) {
            throw new GradingConflictException("Le bulletin doit être calculé avant publication");
        }

        Instant publishedAt = clock.instant();
        DocumentData data = documentData(actor.tenantId(), report, validated, publishedAt);
        byte[] content = renderer.render(data, false);
        String digest = sha256(content);
        String name = fileName(report, validated.languageMode(), false);
        String snapshot = writeJson(data);

        int updated =
                jdbc.update(
                        """
                        UPDATE grading_report_card
                        SET status='PUBLISHED',language_mode=?,file_name=?,pdf_content=?,pdf_sha256=?,
                            pdf_size=?,publication_snapshot=?,publication_note=?,signed_by_name=?,
                            signed_by_title=?,published_by_user_id=?,published_at=?,updated_at=?
                        WHERE tenant_id=? AND id=? AND status='CALCULATED'
                        """,
                        validated.languageMode(),
                        name,
                        content,
                        digest,
                        (long) content.length,
                        snapshot,
                        validated.publicationNote(),
                        validated.signerName(),
                        validated.signerTitle(),
                        actor.accountId(),
                        timestamp(publishedAt),
                        timestamp(publishedAt),
                        actor.tenantId(),
                        reportCardId);
        if (updated != 1) {
            throw new GradingConflictException(
                    "Le bulletin a changé pendant la publication, veuillez actualiser");
        }

        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "grading.report-card.published",
                "ReportCard",
                reportCardId.toString(),
                ipAddress,
                Map.of(
                        "studentId", report.studentId(),
                        "termId", report.termId(),
                        "version", report.versionNumber(),
                        "language", validated.languageMode(),
                        "sha256", digest,
                        "bytes", content.length),
                Map.of("status", report.status()),
                Map.of("status", "PUBLISHED", "publishedAt", publishedAt.toString()));
        return publication(actor.tenantId(), reportCardId);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public PdfDocument download(SchoolPrincipal actor, UUID reportCardId) {
        List<PdfDocument> rows =
                jdbc.query(
                        """
                        SELECT pdf_content,file_name,pdf_sha256
                        FROM grading_report_card
                        WHERE tenant_id=? AND id=? AND status='PUBLISHED'
                        """,
                        (rs, row) ->
                                new PdfDocument(
                                        rs.getBytes("pdf_content"),
                                        rs.getString("file_name"),
                                        rs.getString("pdf_sha256"),
                                        false),
                        actor.tenantId(),
                        reportCardId);
        if (rows.isEmpty()) {
            throw new GradingNotFoundException("Bulletin publié introuvable");
        }
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('grade.read')")
    public List<PublicationView> versions(SchoolPrincipal actor, UUID reportCardId) {
        ReportRow selected = report(actor.tenantId(), reportCardId);
        return jdbc.query(
                publicationSql()
                        + " WHERE rc.tenant_id=? AND rc.student_id=? AND rc.academic_term_id=?"
                        + " ORDER BY rc.version_number DESC",
                ReportCardPublicationService::publicationView,
                actor.tenantId(),
                selected.studentId(),
                selected.termId());
    }

    private PublicationView publication(UUID tenantId, UUID reportCardId) {
        List<PublicationView> rows =
                jdbc.query(
                        publicationSql() + " WHERE rc.tenant_id=? AND rc.id=?",
                        ReportCardPublicationService::publicationView,
                        tenantId,
                        reportCardId);
        if (rows.isEmpty()) throw new GradingNotFoundException("Bulletin introuvable");
        return rows.getFirst();
    }

    private static String publicationSql() {
        return """
        SELECT rc.id,rc.student_id,CONCAT(p.first_name,' ',p.last_name) AS student_name,
               rc.academic_term_id,rc.status,rc.version_number,rc.supersedes_report_card_id,
               rc.language_mode,rc.file_name,rc.pdf_sha256,rc.pdf_size,rc.signed_by_name,
               rc.signed_by_title,rc.publication_note,rc.published_at
        FROM grading_report_card rc
        JOIN student_profile sp ON sp.id=rc.student_id AND sp.tenant_id=rc.tenant_id
        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
        """;
    }

    private DocumentData documentData(
            UUID tenantId, ReportRow report, PublicationCommand command, Instant generatedAt) {
        List<SubjectLine> subjects = subjects(tenantId, report);
        List<CompetencyLine> competencies = competencies(tenantId, report);
        AttendanceSummary attendance = attendance(tenantId, report);
        String appreciation = generalAppreciation(tenantId, report.studentId(), report.termId());
        return new DocumentData(
                report.id(),
                report.versionNumber(),
                command.languageMode(),
                report.schoolName(),
                report.legalName(),
                report.logoReference(),
                report.schoolAddress(),
                report.studentName(),
                report.studentIdentifier(),
                report.classroomName(),
                report.gradeLevelName(),
                report.academicYearName(),
                report.termName(),
                report.termStartsOn(),
                report.termEndsOn(),
                report.overallAverage(),
                report.classRank(),
                report.classSize(),
                subjects,
                competencies,
                attendance.absences(),
                attendance.excusedAbsences(),
                attendance.lates(),
                appreciation,
                command.signerName(),
                command.signerTitle(),
                report.templateName(),
                command.publicationNote(),
                report.layout(),
                generatedAt);
    }

    private List<SubjectLine> subjects(UUID tenantId, ReportRow report) {
        List<SubjectResult> results =
                jdbc.query(
                        """
                        SELECT s.id,s.code,s.name_fr,s.name_ar,r.average,r.coefficient,
                               r.teacher_comment
                        FROM grading_report_card_subject_result r
                        JOIN timetable_subject s ON s.id=r.subject_id AND s.tenant_id=r.tenant_id
                        WHERE r.tenant_id=? AND r.report_card_id=?
                        ORDER BY s.name_fr
                        """,
                        (rs, row) ->
                                new SubjectResult(
                                        uuid(rs, "id"),
                                        rs.getString("code"),
                                        rs.getString("name_fr"),
                                        rs.getString("name_ar"),
                                        rs.getBigDecimal("average"),
                                        rs.getBigDecimal("coefficient"),
                                        rs.getString("teacher_comment")),
                        tenantId,
                        report.id());
        List<SubjectLine> lines = new ArrayList<>();
        for (SubjectResult result : results) {
            List<GradeLine> grades =
                    jdbc.query(
                            """
                            SELECT a.title,a.assessment_date,g.raw_score,a.maximum_score,g.grade_status
                            FROM grading_grade g
                            JOIN grading_assessment a
                              ON a.id=g.assessment_id AND a.tenant_id=g.tenant_id
                            WHERE g.tenant_id=? AND g.student_id=? AND a.classroom_id=?
                              AND a.academic_term_id=? AND a.subject_id=?
                            ORDER BY a.assessment_date,a.title
                            """,
                            (rs, row) ->
                                    new GradeLine(
                                            rs.getString("title"),
                                            rs.getObject("assessment_date", LocalDate.class),
                                            rs.getBigDecimal("raw_score"),
                                            rs.getBigDecimal("maximum_score"),
                                            rs.getString("grade_status")),
                            tenantId,
                            report.studentId(),
                            report.classroomId(),
                            report.termId(),
                            result.subjectId());
            lines.add(
                    new SubjectLine(
                            result.code(),
                            result.nameFr(),
                            result.nameAr(),
                            result.average(),
                            result.coefficient(),
                            grades,
                            result.teacherComment()));
        }
        return lines;
    }

    private List<CompetencyLine> competencies(UUID tenantId, ReportRow report) {
        return jdbc.query(
                """
                SELECT c.name_fr,c.name_ar,e.level_code,e.observation
                FROM grading_competency_evaluation e
                JOIN grading_competency c ON c.id=e.competency_id AND c.tenant_id=e.tenant_id
                WHERE e.tenant_id=? AND e.student_id=? AND e.academic_term_id=?
                ORDER BY c.sequence_number,e.evaluated_at DESC
                """,
                (rs, row) ->
                        new CompetencyLine(
                                rs.getString("name_fr"),
                                rs.getString("name_ar"),
                                rs.getString("level_code"),
                                rs.getString("observation")),
                tenantId,
                report.studentId(),
                report.termId());
    }

    private AttendanceSummary attendance(UUID tenantId, ReportRow report) {
        Integer absences =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM attendance_absence
                        WHERE tenant_id=? AND student_id=? AND occurred_on BETWEEN ? AND ?
                        """,
                        Integer.class,
                        tenantId,
                        report.studentId(),
                        report.termStartsOn(),
                        report.termEndsOn());
        Integer excused =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM attendance_absence
                        WHERE tenant_id=? AND student_id=? AND resolution_status='EXCUSED'
                          AND occurred_on BETWEEN ? AND ?
                        """,
                        Integer.class,
                        tenantId,
                        report.studentId(),
                        report.termStartsOn(),
                        report.termEndsOn());
        Integer lates =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM attendance_late_arrival l
                        JOIN attendance_record r
                          ON r.id=l.attendance_record_id AND r.tenant_id=l.tenant_id
                        JOIN attendance_session s ON s.id=r.session_id AND s.tenant_id=r.tenant_id
                        WHERE l.tenant_id=? AND r.student_id=? AND s.session_date BETWEEN ? AND ?
                        """,
                        Integer.class,
                        tenantId,
                        report.studentId(),
                        report.termStartsOn(),
                        report.termEndsOn());
        return new AttendanceSummary(value(absences), value(excused), value(lates));
    }

    private String generalAppreciation(UUID tenantId, UUID studentId, UUID termId) {
        List<String> values =
                jdbc.query(
                        """
                        SELECT body FROM grading_teacher_comment
                        WHERE tenant_id=? AND student_id=? AND academic_term_id=?
                          AND subject_id IS NULL AND comment_type='GENERAL'
                        ORDER BY created_at DESC
                        """,
                        (rs, row) -> rs.getString("body"),
                        tenantId,
                        studentId,
                        termId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private ReportRow report(UUID tenantId, UUID reportCardId) {
        List<ReportRow> rows =
                jdbc.query(
                        """
                        SELECT rc.id,rc.student_id,rc.academic_term_id,rc.classroom_id,rc.status,
                               rc.overall_average,rc.class_rank,rc.class_size,rc.version_number,
                               so.name AS school_name,so.legal_name,so.logo_object_key,
                               so.address_line1,so.city,
                               CONCAT(p.first_name,' ',p.last_name) AS student_name,
                               COALESCE((SELECT si.identifier_value FROM student_identifier si
                                   WHERE si.tenant_id=rc.tenant_id AND si.student_id=rc.student_id
                                   ORDER BY si.primary_identifier DESC,si.created_at LIMIT 1),
                                   CAST(rc.student_id AS VARCHAR)) AS student_identifier,
                               c.name AS classroom_name,gl.name AS grade_level_name,
                               ay.name AS academic_year_name,t.name AS term_name,
                               t.starts_on AS term_starts_on,t.ends_on AS term_ends_on,
                               rt.name AS template_name,rt.layout_config
                        FROM grading_report_card rc
                        JOIN student_profile sp ON sp.id=rc.student_id AND sp.tenant_id=rc.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        JOIN classroom c ON c.id=rc.classroom_id AND c.tenant_id=rc.tenant_id
                        JOIN grade_level gl ON gl.id=c.grade_level_id AND gl.tenant_id=c.tenant_id
                        JOIN academic_year ay ON ay.id=rc.academic_year_id AND ay.tenant_id=rc.tenant_id
                        JOIN academic_term t ON t.id=rc.academic_term_id AND t.tenant_id=rc.tenant_id
                        JOIN school_organization so ON so.tenant_id=rc.tenant_id
                        LEFT JOIN grading_report_card_template rt
                          ON rt.id=rc.template_id AND rt.tenant_id=rc.tenant_id
                        WHERE rc.tenant_id=? AND rc.id=?
                        """,
                        (rs, row) ->
                                new ReportRow(
                                        uuid(rs, "id"),
                                        uuid(rs, "student_id"),
                                        uuid(rs, "academic_term_id"),
                                        uuid(rs, "classroom_id"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("overall_average"),
                                        nullableInteger(rs, "class_rank"),
                                        nullableInteger(rs, "class_size"),
                                        rs.getInt("version_number"),
                                        rs.getString("school_name"),
                                        rs.getString("legal_name"),
                                        rs.getString("logo_object_key"),
                                        address(
                                                rs.getString("address_line1"),
                                                rs.getString("city")),
                                        rs.getString("student_name"),
                                        rs.getString("student_identifier"),
                                        rs.getString("classroom_name"),
                                        rs.getString("grade_level_name"),
                                        rs.getString("academic_year_name"),
                                        rs.getString("term_name"),
                                        rs.getObject("term_starts_on", LocalDate.class),
                                        rs.getObject("term_ends_on", LocalDate.class),
                                        rs.getString("template_name"),
                                        readMap(rs.getString("layout_config"))),
                        tenantId,
                        reportCardId);
        if (rows.isEmpty()) throw new GradingNotFoundException("Bulletin introuvable");
        return rows.getFirst();
    }

    private PublicationCommand validate(PublicationCommand command, String defaultSigner) {
        if (command == null) throw new GradingValidationException("La publication est requise");
        String language = normalize(command.languageMode());
        if (!LANGUAGE_MODES.contains(language)) {
            throw new GradingValidationException("La langue doit être FR, AR ou BILINGUAL");
        }
        String signer = clean(command.signerName());
        if (signer == null) signer = clean(defaultSigner);
        if (signer == null || signer.length() < 2 || signer.length() > 180) {
            throw new GradingValidationException("Le nom du signataire est invalide");
        }
        String title = limited(command.signerTitle(), 180, "La fonction du signataire");
        String note = limited(command.publicationNote(), 500, "La note de publication");
        return new PublicationCommand(language, signer, title, note);
    }

    private String fileName(ReportRow report, String language, boolean preview) {
        String student = slug(report.studentIdentifier());
        String suffix = preview ? "-apercu" : "";
        return "bulletin-"
                + student
                + "-v"
                + report.versionNumber()
                + "-"
                + language.toLowerCase(Locale.ROOT)
                + suffix
                + ".pdf";
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "L'instantané du bulletin n'a pas pu être sérialisé", exception);
        }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try {
            return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Le modèle du bulletin est invalide", exception);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponible", exception);
        }
    }

    private static String slug(String value) {
        String ascii =
                Normalizer.normalize(value, Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        if (ascii.isBlank()) {
            ascii = HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
        }
        return ascii.length() > 60 ? ascii.substring(0, 60) : ascii;
    }

    private static String address(String line, String city) {
        if (line == null || line.isBlank()) return city;
        if (city == null || city.isBlank()) return line;
        return line + ", " + city;
    }

    private static String limited(String value, int maximum, String label) {
        String cleaned = clean(value);
        if (cleaned != null && cleaned.length() > maximum) {
            throw new GradingValidationException(label + " dépasse " + maximum + " caractères");
        }
        return cleaned;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static UUID nullableUuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null
                ? null
                : value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static PublicationView publicationView(ResultSet rs, int row) throws SQLException {
        return new PublicationView(
                uuid(rs, "id"),
                uuid(rs, "student_id"),
                rs.getString("student_name"),
                uuid(rs, "academic_term_id"),
                rs.getString("status"),
                rs.getInt("version_number"),
                nullableUuid(rs, "supersedes_report_card_id"),
                rs.getString("language_mode"),
                rs.getString("file_name"),
                rs.getString("pdf_sha256"),
                rs.getObject("pdf_size") == null ? null : rs.getLong("pdf_size"),
                rs.getString("signed_by_name"),
                rs.getString("signed_by_title"),
                rs.getString("publication_note"),
                nullableInstant(rs, "published_at"));
    }

    public record PublicationCommand(
            String languageMode, String signerName, String signerTitle, String publicationNote) {}

    public record PdfDocument(byte[] content, String fileName, String sha256, boolean preview) {}

    public record PublicationView(
            UUID id,
            UUID studentId,
            String studentName,
            UUID academicTermId,
            String status,
            int versionNumber,
            UUID supersedesReportCardId,
            String languageMode,
            String fileName,
            String pdfSha256,
            Long pdfSize,
            String signedByName,
            String signedByTitle,
            String publicationNote,
            Instant publishedAt) {}

    private record ReportRow(
            UUID id,
            UUID studentId,
            UUID termId,
            UUID classroomId,
            String status,
            BigDecimal overallAverage,
            Integer classRank,
            Integer classSize,
            int versionNumber,
            String schoolName,
            String legalName,
            String logoReference,
            String schoolAddress,
            String studentName,
            String studentIdentifier,
            String classroomName,
            String gradeLevelName,
            String academicYearName,
            String termName,
            LocalDate termStartsOn,
            LocalDate termEndsOn,
            String templateName,
            Map<String, Object> layout) {}

    private record SubjectResult(
            UUID subjectId,
            String code,
            String nameFr,
            String nameAr,
            BigDecimal average,
            BigDecimal coefficient,
            String teacherComment) {}

    private record AttendanceSummary(int absences, int excusedAbsences, int lates) {}
}
