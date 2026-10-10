package ma.schoolz.erp.identity.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.PermissionCode;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemRoleProvisioner {

    private final RoleRepository roles;
    private final Clock clock;

    public SystemRoleProvisioner(RoleRepository roles, Clock clock) {
        this.roles = roles;
        this.clock = clock;
    }

    @Transactional
    public Map<String, Role> provision(IdentityTenant tenant) {
        Map<String, Set<String>> definitions = new LinkedHashMap<>();
        Set<String> all = PermissionCode.valuesSet();
        Set<String> administration =
                Set.of(
                        "user.read",
                        "user.write",
                        "user.disable",
                        "role.read",
                        "audit.read",
                        "organization.read",
                        "organization.write",
                        "campus.read",
                        "campus.write",
                        "academic-structure.read",
                        "academic-structure.write",
                        "person.read",
                        "person.write",
                        "student.read",
                        "student.write",
                        "data-import.read",
                        "data-import.write",
                        "admissions.read",
                        "admissions.write",
                        "enrollment.read",
                        "enrollment.write",
                        "childcare.read",
                        "childcare.write",
                        "childcare.publish",
                        "attendance.read",
                        "attendance.write",
                        "attendance.manage",
                        "discipline.write",
                        "timetable.read",
                        "timetable.write",
                        "timetable.optimize",
                        "timetable.publish",
                        "grade.read",
                        "grade.write",
                        "grade.configure",
                        "report-card.calculate",
                        "report-card.publish",
                        "homework.read",
                        "homework.write",
                        "homework.publish",
                        "teacher-portal.read",
                        "invoice.read",
                        "invoice.create",
                        "invoice.adjust",
                        "payment.record",
                        "payment.reverse",
                        "collections.read",
                        "collections.manage",
                        "online-payment.read",
                        "online-payment.manage",
                        "online-payment.refund",
                        "communication.read",
                        "communication.write",
                        "communication.announce",
                        "communication.manage",
                        "document.read",
                        "document.write",
                        "document.generate",
                        "document.share",
                        "document.sign",
                        "transport.read",
                        "transport.manage",
                        "transport.attendance",
                        "transport.position",
                        "cafeteria.read",
                        "cafeteria.manage",
                        "cafeteria.attendance",
                        "cafeteria.health",
                        "activity.read",
                        "activity.manage",
                        "health.read",
                        "health.sensitive.read",
                        "health.write",
                        "health.vaccination",
                        "health.configure",
                        "hr.read",
                        "hr.write",
                        "hr.leave.review",
                        "hr.attendance.manage",
                        "payroll.read",
                        "payroll.configure",
                        "payroll.run",
                        "payroll.finalize",
                        "payroll.export",
                        "accounting.read",
                        "accounting.configure",
                        "accounting.post",
                        "accounting.reconcile",
                        "procurement.read",
                        "procurement.request",
                        "procurement.approve",
                        "procurement.order",
                        "procurement.receive",
                        "procurement.invoice",
                        "procurement.pay",
                        "procurement.configure",
                        "inventory.read",
                        "inventory.write",
                        "inventory.assign",
                        "inventory.adjust",
                        "inventory.configure",
                        "library.read",
                        "library.catalog.manage",
                        "library.circulation.manage",
                        "library.fine.manage",
                        "library.configure",
                        "analytics.read",
                        "early-warning.read",
                        "early-warning.manage",
                        "workflow.read",
                        "workflow.manage",
                        "integration.read",
                        "integration.manage",
                        "custom-field.read",
                        "custom-field.configure",
                        "custom-field.value.write",
                        "business-configuration.read",
                        "business-configuration.manage",
                        "public-api.read",
                        "public-api.manage",
                        "webhook.read",
                        "webhook.manage",
                        "global-search.read",
                        "global-search.manage");
        definitions.put("SUPER_ADMIN", all);
        definitions.put("ORGANIZATION_OWNER", all);
        definitions.put("SCHOOL_DIRECTOR", all);
        definitions.put("CAMPUS_MANAGER", administration);
        definitions.put(
                "ADMINISTRATIVE_STAFF",
                Set.of(
                        "user.read",
                        "person.read",
                        "person.write",
                        "student.read",
                        "student.write",
                        "data-import.read",
                        "data-import.write",
                        "admissions.read",
                        "enrollment.read",
                        "attendance.read",
                        "attendance.manage",
                        "timetable.read",
                        "timetable.write",
                        "grade.read",
                        "report-card.calculate",
                        "report-card.publish",
                        "homework.read",
                        "homework.write",
                        "homework.publish",
                        "communication.read",
                        "communication.write",
                        "communication.announce",
                        "communication.manage",
                        "document.read",
                        "document.write",
                        "document.generate",
                        "document.share",
                        "document.sign"));
        definitions.put(
                "ACCOUNTANT",
                Set.of(
                        "invoice.read",
                        "invoice.create",
                        "invoice.adjust",
                        "payment.record",
                        "payment.reverse",
                        "collections.read",
                        "collections.manage",
                        "online-payment.read",
                        "online-payment.manage",
                        "online-payment.refund",
                        "payroll.read",
                        "payroll.export",
                        "accounting.read",
                        "accounting.configure",
                        "accounting.post",
                        "accounting.reconcile",
                        "procurement.read",
                        "procurement.invoice",
                        "procurement.pay"));
        definitions.put(
                "PURCHASING_MANAGER",
                Set.of(
                        "procurement.read",
                        "procurement.request",
                        "procurement.approve",
                        "procurement.order",
                        "procurement.receive",
                        "procurement.invoice",
                        "procurement.configure"));
        definitions.put("PURCHASE_REQUESTER", Set.of("procurement.read", "procurement.request"));
        definitions.put(
                "INVENTORY_MANAGER",
                Set.of(
                        "inventory.read",
                        "inventory.write",
                        "inventory.assign",
                        "inventory.adjust",
                        "inventory.configure"));
        definitions.put(
                "LIBRARIAN",
                Set.of(
                        "student.read",
                        "library.read",
                        "library.catalog.manage",
                        "library.circulation.manage",
                        "library.fine.manage",
                        "library.configure"));
        definitions.put("ANALYTICS_VIEWER", Set.of("analytics.read"));
        definitions.put(
                "EARLY_WARNING_MANAGER",
                Set.of("student.read", "early-warning.read", "early-warning.manage"));
        definitions.put(
                "WORKFLOW_MANAGER", Set.of("workflow.read", "workflow.manage", "audit.read"));
        definitions.put(
                "INTEGRATION_MANAGER",
                Set.of("integration.read", "integration.manage", "data-import.read"));
        definitions.put(
                "CUSTOM_FIELD_MANAGER",
                Set.of(
                        "custom-field.read",
                        "custom-field.configure",
                        "custom-field.value.write",
                        "student.read"));
        definitions.put(
                "CONFIGURATION_MANAGER",
                Set.of(
                        "business-configuration.read",
                        "business-configuration.manage",
                        "audit.read"));
        definitions.put(
                "API_MANAGER", Set.of("public-api.read", "public-api.manage", "audit.read"));
        definitions.put("WEBHOOK_MANAGER", Set.of("webhook.read", "webhook.manage", "audit.read"));
        definitions.put(
                "SEARCH_MANAGER",
                Set.of(
                        "global-search.read",
                        "global-search.manage",
                        "student.read",
                        "person.read",
                        "document.read"));
        definitions.put("TASK_MANAGER", Set.of("task.read", "task.manage"));
        definitions.put("CALENDAR_MANAGER", Set.of("calendar.read", "calendar.manage"));
        definitions.put(
                "PRIVACY_MANAGER",
                Set.of("privacy.read", "privacy.manage", "privacy.export", "audit.read"));
        definitions.put(
                "ADMISSIONS_OFFICER",
                Set.of(
                        "person.read",
                        "person.write",
                        "student.read",
                        "student.write",
                        "data-import.read",
                        "data-import.write",
                        "admissions.read",
                        "admissions.write",
                        "enrollment.read",
                        "enrollment.write"));
        definitions.put(
                "TEACHER",
                Set.of(
                        "student.read",
                        "grade.read",
                        "grade.write",
                        "attendance.read",
                        "attendance.write",
                        "discipline.write",
                        "timetable.read",
                        "teacher-portal.read",
                        "homework.read",
                        "homework.write",
                        "homework.publish",
                        "communication.read",
                        "communication.write",
                        "communication.announce",
                        "task.read",
                        "calendar.read",
                        "custom-field.read"));
        definitions.put(
                "EDUCATOR",
                Set.of(
                        "student.read",
                        "attendance.read",
                        "attendance.write",
                        "childcare.read",
                        "childcare.write",
                        "childcare.publish"));
        definitions.put(
                "SUPERVISOR",
                Set.of(
                        "student.read",
                        "attendance.read",
                        "attendance.write",
                        "attendance.manage",
                        "discipline.write",
                        "timetable.read",
                        "grade.read",
                        "report-card.calculate",
                        "report-card.publish",
                        "homework.read",
                        "homework.write",
                        "homework.publish",
                        "childcare.read",
                        "childcare.write",
                        "childcare.publish",
                        "communication.read",
                        "communication.write",
                        "communication.announce",
                        "early-warning.read",
                        "early-warning.manage",
                        "workflow.read",
                        "workflow.manage",
                        "integration.read",
                        "integration.manage",
                        "custom-field.read",
                        "custom-field.value.write"));
        definitions.put(
                "NURSE",
                Set.of(
                        "student.read",
                        "attendance.read",
                        "childcare.read",
                        "childcare.write",
                        "childcare.medication",
                        "health.read",
                        "health.sensitive.read",
                        "health.write",
                        "health.vaccination",
                        "custom-field.read",
                        "custom-field.value.write"));
        definitions.put(
                "TRANSPORT_MANAGER",
                Set.of(
                        "student.read",
                        "transport.read",
                        "transport.manage",
                        "transport.attendance",
                        "transport.position"));
        definitions.put(
                "DRIVER", Set.of("transport.read", "transport.attendance", "transport.position"));
        definitions.put(
                "CAFETERIA_MANAGER",
                Set.of(
                        "student.read",
                        "cafeteria.read",
                        "cafeteria.manage",
                        "cafeteria.attendance",
                        "cafeteria.health"));
        definitions.put(
                "HR_MANAGER",
                Set.of(
                        "user.read",
                        "person.read",
                        "hr.read",
                        "hr.write",
                        "hr.leave.review",
                        "hr.attendance.manage",
                        "payroll.read",
                        "payroll.configure",
                        "payroll.run",
                        "document.read",
                        "document.write"));
        definitions.put("PARENT", Set.of());
        definitions.put("STUDENT", Set.of("student-portal.read", "student-portal.write"));

        Map<String, Role> result = new LinkedHashMap<>();
        definitions.forEach(
                (code, permissions) -> {
                    Role role =
                            roles.findByTenantIdAndCode(tenant.id(), code)
                                    .orElseGet(
                                            () ->
                                                    roles.save(
                                                            Role.create(
                                                                    tenant.id(),
                                                                    code,
                                                                    roleName(code),
                                                                    true,
                                                                    permissions,
                                                                    clock.instant())));
                    if (!role.permissions().equals(permissions)) {
                        role.update(roleName(code), permissions);
                        roles.save(role);
                    }
                    result.put(code, role);
                });
        return result;
    }

    private static String roleName(String code) {
        String value = code.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
