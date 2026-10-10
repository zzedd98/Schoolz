package ma.schoolz.erp.grading.application;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ma.schoolz.erp.identity.TenantDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(90)
public class DevGradingSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final TenantDirectory tenants;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;

    public DevGradingSeeder(
            JdbcTemplate jdbc,
            TenantDirectory tenants,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) return;
        UUID tenantId =
                tenants.findActiveBySlug(tenantSlug)
                        .orElseThrow(() -> new IllegalStateException("Demo tenant was not seeded"))
                        .id();
        Integer existing =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM grading_policy WHERE tenant_id=? AND"
                                + " code='PRIMARY-HYBRID'",
                        Integer.class,
                        tenantId);
        if (existing != null && existing > 0) return;

        Instant now = clock.instant();
        UUID actorId =
                id(
                        "SELECT id FROM identity_user_account WHERE tenant_id=? AND"
                                + " normalized_email='admin@schoolz.ma'",
                        tenantId);
        UUID yearId =
                id("SELECT id FROM academic_year WHERE tenant_id=? AND code='2026-2027'", tenantId);
        UUID termId =
                id(
                        "SELECT id FROM academic_term WHERE tenant_id=? AND academic_year_id=? AND"
                                + " code='T1'",
                        tenantId,
                        yearId);
        UUID preschoolId =
                id("SELECT id FROM school_level WHERE tenant_id=? AND code='MATERNELLE'", tenantId);
        UUID primaryId =
                id("SELECT id FROM school_level WHERE tenant_id=? AND code='PRIMAIRE'", tenantId);
        UUID middleId =
                id("SELECT id FROM school_level WHERE tenant_id=? AND code='COLLEGE'", tenantId);
        UUID gradeId = id("SELECT id FROM grade_level WHERE tenant_id=? AND code='1AP'", tenantId);
        UUID classroomId =
                id("SELECT id FROM classroom WHERE tenant_id=? AND code='1AP-A'", tenantId);
        UUID mathId =
                id("SELECT id FROM timetable_subject WHERE tenant_id=? AND code='MATH'", tenantId);
        UUID frenchId =
                id("SELECT id FROM timetable_subject WHERE tenant_id=? AND code='FR'", tenantId);
        UUID mathTeacher =
                id(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND"
                                + " employee_number='DEMO-TEACH-001'",
                        tenantId);
        UUID frenchTeacher =
                id(
                        "SELECT id FROM staff_profile WHERE tenant_id=? AND"
                                + " employee_number='DEMO-TEACH-002'",
                        tenantId);

        UUID numericScale = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO grading_grade_scale
                    (id,tenant_id,code,name,scale_type,minimum_score,maximum_score,passing_score,
                     rounding_scale,competency_levels,active,created_at,updated_at)
                VALUES (?, ?, 'NOTE-20', 'Notation sur 20', 'NUMERIC', 0, 20, 10, 2, '[]',
                        TRUE, ?, ?)
                """,
                numericScale,
                tenantId,
                timestamp(now),
                timestamp(now));
        UUID competencyScale = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO grading_grade_scale
                    (id,tenant_id,code,name,scale_type,minimum_score,maximum_score,passing_score,
                     rounding_scale,competency_levels,active,created_at,updated_at)
                VALUES (?, ?, 'COMP-4', 'Maîtrise en quatre niveaux', 'COMPETENCY', NULL, NULL,
                        NULL, 0, ?, TRUE, ?, ?)
                """,
                competencyScale,
                tenantId,
                """
                [{"code":"EMERGING","labelFr":"Émergent","labelAr":"في طور البداية","sequenceNumber":1,"color":"#dc6b5d"},{"code":"DEVELOPING","labelFr":"En développement","labelAr":"في طور التطور","sequenceNumber":2,"color":"#d5a22f"},{"code":"ACQUIRED","labelFr":"Acquis","labelAr":"مكتسب","sequenceNumber":3,"color":"#2f8f68"},{"code":"MASTERED","labelFr":"Maîtrisé","labelAr":"متقن","sequenceNumber":4,"color":"#236b53"}]
                """
                        .strip(),
                timestamp(now),
                timestamp(now));

        policy(
                tenantId,
                preschoolId,
                "PRESCHOOL-COMPETENCY",
                "Maternelle — compétences",
                "COMPETENCY_ONLY",
                null,
                competencyScale,
                "WEIGHTED_MEAN",
                "{\"roundingScale\":2}",
                false,
                true,
                now);
        policy(
                tenantId,
                primaryId,
                "PRIMARY-HYBRID",
                "Primaire — notes et compétences",
                "HYBRID",
                numericScale,
                competencyScale,
                "CATEGORY_WEIGHTED",
                "{\"categoryWeights\":{\"CONTINUOUS\":60,\"EXAM\":40},\"missingCategoryMode\":\"RENORMALIZE\",\"dropLowest\":0,\"roundingScale\":2,\"overallUseCoefficients\":true,\"rankingMethod\":\"COMPETITION\"}",
                false,
                true,
                now);
        policy(
                tenantId,
                middleId,
                "SECONDARY-NUMERIC",
                "Collège / lycée — notes et coefficients",
                "NUMERIC",
                numericScale,
                null,
                "CATEGORY_WEIGHTED",
                "{\"categoryWeights\":{\"CONTINUOUS\":40,\"EXAM\":60},\"missingCategoryMode\":\"RENORMALIZE\",\"roundingScale\":2,\"overallUseCoefficients\":true,\"rankingMethod\":\"COMPETITION\"}",
                true,
                true,
                now);
        childcarePolicy(tenantId, now);

        UUID continuousType =
                assessmentType(
                        tenantId,
                        "CONTROLE",
                        "Contrôle continu",
                        "المراقبة المستمرة",
                        "CONTINUOUS",
                        true,
                        false,
                        now);
        UUID examType =
                assessmentType(tenantId, "EXAMEN", "Examen", "الامتحان", "EXAM", true, false, now);
        assessmentType(
                tenantId,
                "OBSERVATION",
                "Observation de compétence",
                "ملاحظة المهارة",
                "COMPETENCY",
                false,
                true,
                now);
        coefficient(tenantId, yearId, gradeId, mathId, new BigDecimal("3"), now);
        coefficient(tenantId, yearId, gradeId, frenchId, new BigDecimal("2"), now);

        UUID problemSolving =
                competency(
                        tenantId,
                        primaryId,
                        mathId,
                        "MATH-RESOLUTION",
                        "Résoudre un problème en explicitant sa démarche",
                        "حل مسألة مع شرح المنهجية",
                        1,
                        now);
        UUID oralCommunication =
                competency(
                        tenantId,
                        primaryId,
                        frenchId,
                        "FR-ORAL",
                        "S'exprimer clairement à l'oral",
                        "التعبير الشفهي بوضوح",
                        2,
                        now);
        template(tenantId, primaryId, now);

        UUID mathContinuous =
                assessment(
                        tenantId,
                        yearId,
                        termId,
                        classroomId,
                        mathId,
                        continuousType,
                        mathTeacher,
                        actorId,
                        "Résolution de problèmes — 1",
                        LocalDate.of(2026, 9, 24),
                        BigDecimal.valueOf(20),
                        BigDecimal.ONE,
                        now);
        UUID mathExam =
                assessment(
                        tenantId,
                        yearId,
                        termId,
                        classroomId,
                        mathId,
                        examType,
                        mathTeacher,
                        actorId,
                        "Évaluation de synthèse",
                        LocalDate.of(2026, 10, 22),
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(2),
                        now);
        UUID frenchContinuous =
                assessment(
                        tenantId,
                        yearId,
                        termId,
                        classroomId,
                        frenchId,
                        continuousType,
                        frenchTeacher,
                        actorId,
                        "Lecture et dictée",
                        LocalDate.of(2026, 10, 8),
                        BigDecimal.valueOf(20),
                        BigDecimal.ONE,
                        now);

        List<UUID> students =
                jdbc.query(
                        """
                        SELECT se.student_id
                        FROM student_enrollment se
                        LEFT JOIN childcare_profile cp
                          ON cp.student_id=se.student_id AND cp.tenant_id=se.tenant_id
                        JOIN student_profile sp
                          ON sp.id=se.student_id AND sp.tenant_id=se.tenant_id
                        JOIN person p ON p.id=sp.person_id AND p.tenant_id=sp.tenant_id
                        WHERE se.tenant_id=? AND se.classroom_id=? AND se.status='ACTIVE'
                          AND cp.id IS NULL
                        ORDER BY p.last_name,p.first_name
                        """,
                        (result, row) -> result.getObject("student_id", UUID.class),
                        tenantId,
                        classroomId);
        BigDecimal[] continuous = {
            new BigDecimal("16"), new BigDecimal("13"), new BigDecimal("18")
        };
        BigDecimal[] exams = {new BigDecimal("17"), new BigDecimal("14"), new BigDecimal("19")};
        BigDecimal[] french = {new BigDecimal("14.5"), new BigDecimal("12"), new BigDecimal("16")};
        String[] levels = {"ACQUIRED", "DEVELOPING", "MASTERED"};
        for (int index = 0; index < students.size(); index++) {
            UUID studentId = students.get(index);
            BigDecimal first = continuous[index % continuous.length];
            grade(tenantId, mathContinuous, studentId, first, actorId, index == 0, now);
            grade(tenantId, mathExam, studentId, exams[index % exams.length], actorId, false, now);
            grade(
                    tenantId,
                    frenchContinuous,
                    studentId,
                    french[index % french.length],
                    actorId,
                    false,
                    now);
            competencyEvaluation(
                    tenantId,
                    problemSolving,
                    studentId,
                    termId,
                    mathContinuous,
                    mathTeacher,
                    actorId,
                    levels[index % levels.length],
                    "Démarche observée pendant l'activité guidée.",
                    now);
            competencyEvaluation(
                    tenantId,
                    oralCommunication,
                    studentId,
                    termId,
                    null,
                    frenchTeacher,
                    actorId,
                    index % 2 == 0 ? "ACQUIRED" : "DEVELOPING",
                    "Participation orale régulière.",
                    now);
            comment(
                    tenantId,
                    studentId,
                    termId,
                    mathTeacher,
                    actorId,
                    "GENERAL",
                    "Travail sérieux ; poursuivre les efforts avec régularité.",
                    now);
        }
    }

    private void policy(
            UUID tenantId,
            UUID schoolLevelId,
            String code,
            String name,
            String mode,
            UUID numericScale,
            UUID competencyScale,
            String strategy,
            String config,
            boolean rankings,
            boolean reportCards,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_policy
                    (id,tenant_id,scope_type,scope_key,school_level_id,code,name,evaluation_mode,
                     grade_scale_id,competency_scale_id,calculation_strategy,calculation_config,
                     rankings_enabled,report_cards_enabled,active,created_at,updated_at)
                VALUES (?, ?, 'SCHOOL_LEVEL', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                schoolLevelId.toString(),
                schoolLevelId,
                code,
                name,
                mode,
                numericScale,
                competencyScale,
                strategy,
                config,
                rankings,
                reportCards,
                timestamp(now),
                timestamp(now));
    }

    private void childcarePolicy(UUID tenantId, Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_policy
                    (id,tenant_id,scope_type,scope_key,school_level_id,code,name,evaluation_mode,
                     grade_scale_id,competency_scale_id,calculation_strategy,calculation_config,
                     rankings_enabled,report_cards_enabled,active,created_at,updated_at)
                VALUES (?, ?, 'CHILDCARE', 'CHILDCARE', NULL, 'CHILDCARE-NO-GRADE',
                        'Crèche — aucune note', 'NONE', NULL, NULL, 'WEIGHTED_MEAN', '{}',
                        FALSE, FALSE, TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                timestamp(now),
                timestamp(now));
    }

    private UUID assessmentType(
            UUID tenantId,
            String code,
            String nameFr,
            String nameAr,
            String category,
            boolean numeric,
            boolean competency,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO grading_assessment_type
                    (id,tenant_id,code,name_fr,name_ar,calculation_category,numeric_enabled,
                     competency_enabled,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                code,
                nameFr,
                nameAr,
                category,
                numeric,
                competency,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void coefficient(
            UUID tenantId,
            UUID yearId,
            UUID gradeId,
            UUID subjectId,
            BigDecimal value,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_coefficient
                    (id,tenant_id,academic_year_id,grade_level_id,subject_id,coefficient,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                yearId,
                gradeId,
                subjectId,
                value,
                timestamp(now),
                timestamp(now));
    }

    private UUID competency(
            UUID tenantId,
            UUID schoolLevelId,
            UUID subjectId,
            String code,
            String nameFr,
            String nameAr,
            int sequence,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO grading_competency
                    (id,tenant_id,school_level_id,subject_id,parent_competency_id,code,name_fr,
                     name_ar,description,sequence_number,active,created_at,updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?, ?, NULL, ?, TRUE, ?, ?)
                """,
                id,
                tenantId,
                schoolLevelId,
                subjectId,
                code,
                nameFr,
                nameAr,
                sequence,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void template(UUID tenantId, UUID schoolLevelId, Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_report_card_template
                    (id,tenant_id,school_level_id,code,name,language_mode,layout_config,
                     version_number,active,created_at,updated_at)
                VALUES (?, ?, ?, 'PRIMARY-TRIMESTER', 'Bulletin primaire trimestriel',
                        'BILINGUAL', '{"showRank":false,"showCompetencies":true}', 1,
                        TRUE, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                schoolLevelId,
                timestamp(now),
                timestamp(now));
    }

    private UUID assessment(
            UUID tenantId,
            UUID yearId,
            UUID termId,
            UUID classroomId,
            UUID subjectId,
            UUID typeId,
            UUID teacherId,
            UUID actorId,
            String title,
            LocalDate date,
            BigDecimal maximum,
            BigDecimal coefficient,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO grading_assessment
                    (id,tenant_id,academic_year_id,academic_term_id,classroom_id,subject_id,
                     assessment_type_id,teacher_staff_id,title,description,assessment_date,
                     maximum_score,coefficient,status,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, ?, 'PUBLISHED', ?, ?, ?)
                """,
                id,
                tenantId,
                yearId,
                termId,
                classroomId,
                subjectId,
                typeId,
                teacherId,
                title,
                date,
                maximum,
                coefficient,
                actorId,
                timestamp(now),
                timestamp(now));
        return id;
    }

    private void grade(
            UUID tenantId,
            UUID assessmentId,
            UUID studentId,
            BigDecimal score,
            UUID actorId,
            boolean corrected,
            Instant now) {
        UUID gradeId = UUID.randomUUID();
        BigDecimal original = corrected ? score.subtract(BigDecimal.ONE) : score;
        jdbc.update(
                """
                INSERT INTO grading_grade
                    (id,tenant_id,assessment_id,student_id,raw_score,grade_status,teacher_comment,
                     version,created_by_user_id,updated_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, 'GRADED', NULL, ?, ?, ?, ?, ?)
                """,
                gradeId,
                tenantId,
                assessmentId,
                studentId,
                score,
                corrected ? 1 : 0,
                actorId,
                actorId,
                timestamp(now.minusSeconds(corrected ? 3600 : 0)),
                timestamp(now));
        history(
                tenantId,
                gradeId,
                assessmentId,
                studentId,
                "CREATED",
                null,
                original,
                null,
                "GRADED",
                null,
                actorId,
                now.minusSeconds(corrected ? 3600 : 0));
        if (corrected) {
            history(
                    tenantId,
                    gradeId,
                    assessmentId,
                    studentId,
                    "UPDATED",
                    original,
                    score,
                    "GRADED",
                    "GRADED",
                    "Correction après vérification de la copie",
                    actorId,
                    now);
        }
    }

    private void history(
            UUID tenantId,
            UUID gradeId,
            UUID assessmentId,
            UUID studentId,
            String type,
            BigDecimal previousScore,
            BigDecimal newScore,
            String previousStatus,
            String newStatus,
            String reason,
            UUID actorId,
            Instant changedAt) {
        String before =
                previousScore == null
                        ? null
                        : "{\"rawScore\":"
                                + previousScore
                                + ",\"status\":\""
                                + previousStatus
                                + "\"}";
        String after = "{\"rawScore\":" + newScore + ",\"status\":\"" + newStatus + "\"}";
        jdbc.update(
                """
                INSERT INTO grading_grade_history
                    (id,tenant_id,grade_id,assessment_id,student_id,change_type,previous_score,
                     new_score,previous_status,new_status,reason,actor_user_id,changed_at,
                     before_snapshot,after_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                gradeId,
                assessmentId,
                studentId,
                type,
                previousScore,
                newScore,
                previousStatus,
                newStatus,
                reason,
                actorId,
                timestamp(changedAt),
                before,
                after);
    }

    private void competencyEvaluation(
            UUID tenantId,
            UUID competencyId,
            UUID studentId,
            UUID termId,
            UUID assessmentId,
            UUID teacherId,
            UUID actorId,
            String level,
            String observation,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_competency_evaluation
                    (id,tenant_id,competency_id,student_id,academic_term_id,assessment_id,
                     teacher_staff_id,level_code,observation,evaluated_at,created_by_user_id,
                     created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                competencyId,
                studentId,
                termId,
                assessmentId,
                teacherId,
                level,
                observation,
                timestamp(now),
                actorId,
                timestamp(now),
                timestamp(now));
    }

    private void comment(
            UUID tenantId,
            UUID studentId,
            UUID termId,
            UUID teacherId,
            UUID actorId,
            String type,
            String body,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO grading_teacher_comment
                    (id,tenant_id,student_id,academic_term_id,subject_id,teacher_staff_id,
                     comment_type,body,visible_to_family,created_by_user_id,created_at,updated_at)
                VALUES (?, ?, ?, ?, NULL, ?, ?, ?, TRUE, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                studentId,
                termId,
                teacherId,
                type,
                body,
                actorId,
                timestamp(now),
                timestamp(now));
    }

    private UUID id(String sql, Object... args) {
        List<UUID> rows =
                jdbc.query(sql, (result, row) -> result.getObject("id", UUID.class), args);
        if (rows.isEmpty()) throw new IllegalStateException("Missing demo dependency: " + sql);
        return rows.getFirst();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
