package ma.schoolz.erp.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationIT {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine")
                    .withDatabaseName("school_erp_test")
                    .withUsername("school_erp")
                    .withPassword("school_erp_test");

    @Test
    void allMigrationsApplyToSupportedPostgres() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration")
                        .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(46);

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name LIKE 'privacy_%'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='global_search_document'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='school_calendar_event'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='internal_task'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('webhook_subscription',"
                                        + " 'webhook_event','webhook_delivery',"
                                        + " 'webhook_delivery_attempt','webhook_dead_letter')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(5);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT metadata_value FROM platform_metadata WHERE metadata_key ="
                                        + " ?")) {
            statement.setString(1, "schema_owner");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("schoolz-platform");
            }
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN"
                                        + " ('public_api_client','public_api_request_log')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN"
                                        + " ('business_configuration_item',"
                                        + " 'business_configuration_revision')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('integration_adapter',"
                                    + " 'integration_mapping_profile','integration_transfer_job',"
                                    + " 'integration_transfer_row','integration_export_file')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(5);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('custom_field_definition',"
                                    + " 'custom_field_value')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('workflow_definition',"
                                    + " 'workflow_trigger','workflow_condition','workflow_action',"
                                    + " 'workflow_execution','workflow_execution_log')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('early_warning_rule','early_warning_rule_condition',"
                                    + " 'early_warning_student_signal','early_warning_alert',"
                                    + " 'early_warning_alert_evidence','early_warning_evaluation')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('identity_tenant',"
                                        + " 'identity_user_account', 'identity_refresh_session',"
                                        + " 'identity_audit_log')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(4);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='teacher_portal_profile'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN"
                                        + " ('billing_schedule_payment_allocation',"
                                        + " 'billing_reminder_rule','billing_collection_action')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('communication_conversation',"
                                    + " 'communication_message','communication_announcement',"
                                    + " 'communication_notification',"
                                    + " 'communication_notification_template',"
                                    + " 'communication_notification_preference',"
                                    + " 'communication_notification_delivery',"
                                    + " 'communication_notification_policy',"
                                    + " 'communication_center_preference')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(9);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('library_configuration','library_author',"
                                    + " 'library_category','library_book','library_book_author',"
                                    + " 'library_book_copy','library_loan','library_reservation',"
                                    + " 'library_fine')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(9);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN"
                                        + " ('analytics_snapshot','analytics_dimension_value')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('health_policy','health_profile',"
                                    + " 'health_allergy','health_medication_instruction',"
                                    + " 'health_incident','health_nurse_visit',"
                                    + " 'health_emergency_action','health_vaccination_record')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(8);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('hr_department','hr_position',"
                                    + " 'hr_employee','hr_employment_contract','hr_work_schedule',"
                                    + " 'hr_leave_request','hr_staff_attendance','hr_staff_document',"
                                    + " 'hr_teacher_qualification')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(9);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('payroll_period','payroll_salary_component',"
                                    + " 'payroll_salary_rule','payroll_employee_salary_profile',"
                                    + " 'payroll_profile_component','payroll_run','payroll_payslip',"
                                    + " 'payroll_payslip_line','payroll_salary_adjustment',"
                                    + " 'payroll_accounting_export')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(10);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('accounting_configuration','accounting_chart_of_account',"
                                    + " 'accounting_journal','accounting_fiscal_period',"
                                    + " 'accounting_cost_center','accounting_journal_entry',"
                                    + " 'accounting_journal_line','accounting_supplier',"
                                    + " 'accounting_supplier_invoice','accounting_expense',"
                                    + " 'accounting_bank_account','accounting_reconciliation',"
                                    + " 'accounting_reconciliation_item','accounting_event_mapping')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(14);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('procurement_configuration','procurement_approval_threshold',"
                                    + " 'procurement_purchase_request','procurement_purchase_request_line',"
                                    + " 'procurement_request_approval','procurement_purchase_order',"
                                    + " 'procurement_purchase_order_line','procurement_goods_receipt',"
                                    + " 'procurement_goods_receipt_line','procurement_invoice_match',"
                                    + " 'procurement_supplier_payment')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(11);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN"
                                        + " ('inventory_location','inventory_item',"
                                        + " 'inventory_stock_balance','inventory_stock_movement',"
                                        + " 'inventory_asset','inventory_asset_assignment',"
                                        + " 'inventory_asset_event')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(7);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('document_category',"
                                        + " 'document_file','document_object_blob',"
                                        + " 'document_version','document_access_grant',"
                                        + " 'document_signed_download')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN"
                                    + " ('document_signature_workflow','document_signature_event')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(2);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('transport_vehicle',"
                                    + " 'transport_driver','transport_assistant','transport_route',"
                                    + " 'transport_stop','transport_route_assignment',"
                                    + " 'transport_student_subscription','transport_trip',"
                                    + " 'transport_pickup_event','transport_dropoff_event',"
                                    + " 'transport_vehicle_position')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(11);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('cafeteria_meal_plan',"
                                    + " 'cafeteria_menu','cafeteria_allergen',"
                                    + " 'cafeteria_food_restriction','cafeteria_meal_subscription',"
                                    + " 'cafeteria_meal_attendance','cafeteria_meal_charge')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(7);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('activity_coach','activity',"
                                    + " 'activity_capacity','activity_session','activity_enrollment',"
                                    + " 'activity_waiting_list','activity_charge')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(7);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('billing_online_payment',"
                                        + " 'billing_payment_webhook','billing_online_refund')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(3);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='billing_family_account'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name='family_portal_preference'");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('school_organization',"
                                        + " 'school_campus', 'academic_year', 'academic_term',"
                                        + " 'school_level', 'grade_level', 'physical_room',"
                                        + " 'classroom')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(8);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('childcare_policy',"
                                        + " 'childcare_group', 'childcare_profile',"
                                        + " 'childcare_pickup_person', 'childcare_daily_log',"
                                        + " 'childcare_daily_event')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(6);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('attendance_absence_reason',"
                                    + " 'attendance_session', 'attendance_record',"
                                    + " 'attendance_absence', 'attendance_late_arrival',"
                                    + " 'attendance_early_departure', 'attendance_excused_absence',"
                                    + " 'attendance_parent_notification', 'disciplinary_incident',"
                                    + " 'attendance_sanction', 'teacher_observation')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(11);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('timetable_subject',"
                                    + " 'timetable_schedule', 'timetable_period',"
                                    + " 'timetable_time_slot', 'timetable_room_equipment',"
                                    + " 'timetable_teacher_assignment', 'timetable_lesson',"
                                    + " 'timetable_teacher_availability',"
                                    + " 'timetable_room_availability', 'timetable_constraint')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(10);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('grading_grade_scale',"
                                        + " 'grading_policy', 'grading_assessment_type',"
                                        + " 'grading_coefficient', 'grading_assessment',"
                                        + " 'grading_grade', 'grading_grade_history',"
                                        + " 'grading_competency', 'grading_competency_evaluation',"
                                        + " 'grading_teacher_comment',"
                                        + " 'grading_report_card_template', 'grading_report_card',"
                                        + " 'grading_report_card_subject_result')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(13);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.columns WHERE table_schema"
                                    + " = 'public' AND table_name='grading_report_card' AND"
                                    + " column_name IN"
                                    + " ('supersedes_report_card_id','language_mode',"
                                    + " 'file_name','pdf_content','pdf_sha256','pdf_size',"
                                    + " 'publication_snapshot','publication_note','signed_by_name',"
                                    + " 'signed_by_title','published_by_user_id','published_at')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(12);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                        + " = 'public' AND table_name IN ('lesson_content',"
                                        + " 'homework_assignment', 'homework_attachment',"
                                        + " 'homework_student_status')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(4);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('person', 'person_address',"
                                    + " 'person_contact_method', 'household', 'student_profile',"
                                    + " 'guardian_profile', 'household_relationship',"
                                    + " 'emergency_contact', 'student_enrollment',"
                                    + " 'student_identifier', 'person_custom_field',"
                                    + " 'person_document', 'student_history')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(13);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('data_import_job',"
                                    + " 'data_import_row', 'staff_profile',"
                                    + " 'imported_legacy_payment', 'imported_opening_balance')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(5);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('admission_portal_account',"
                                    + " 'admission_prospect', 'admission_applicant',"
                                    + " 'admission_application', 'admission_application_status',"
                                    + " 'admission_campaign', 'admission_step',"
                                    + " 'admission_document', 'admission_visit',"
                                    + " 'admission_interview', 'admission_test',"
                                    + " 'admission_decision', 'admission_enrollment_offer',"
                                    + " 'admission_notification', 'admission_fee_payment')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(15);
        }

        try (var connection =
                        DriverManager.getConnection(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword());
                var statement =
                        connection.prepareStatement(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema"
                                    + " = 'public' AND table_name IN ('enrollment_workflow',"
                                    + " 'enrollment_contract', 'enrollment_family_confirmation',"
                                    + " 'enrollment_service_selection', 'enrollment_document',"
                                    + " 'enrollment_signature', 'enrollment_fee_payment',"
                                    + " 'enrollment_portal_student_link')");
                var result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(8);
        }
    }
}
