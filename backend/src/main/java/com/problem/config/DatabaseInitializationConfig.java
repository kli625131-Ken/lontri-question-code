package com.problem.config;

import com.problem.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DatabaseInitializationConfig {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ImportService importService;

    @Value("${ops.import.bootstrap-enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${ops.import.bootstrap-file:}")
    private String bootstrapFile;

    @Value("${ops.import.reset-on-bootstrap:false}")
    private boolean resetOnBootstrap;

    @Value("${ops.import.default-remind-after-days:7}")
    private int defaultRemindAfterDays;

    @Bean
    public ApplicationRunner initializeDatabase() {
        return args -> {
            createTables();
            applyProjectMasterDataGovernance();
            applyHistoryDataGovernance();
            ensureDefaultRoles();
            ensureAdminUserWithRole();
            if (bootstrapEnabled && StringUtils.hasText(bootstrapFile)) {
                if (resetOnBootstrap) {
                    importService.resetOperationalData();
                }
                importService.bootstrapFromFile(Path.of(bootstrapFile), defaultRemindAfterDays);
            }
            ensureCustomerProjectSeedData();
        };
    }

    private void createTables() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sys_user (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(50) NOT NULL UNIQUE,
                password VARCHAR(100) NOT NULL,
                real_name VARCHAR(50),
                email VARCHAR(100),
                phone VARCHAR(20),
                avatar_url VARCHAR(255),
                status TINYINT DEFAULT 1,
                role_id BIGINT NULL,
                is_admin TINYINT DEFAULT 0,
                global_search_enabled TINYINT DEFAULT 0,
                account_type VARCHAR(20) DEFAULT 'NORMAL',
                expire_at DATETIME NULL,
                last_login_time DATETIME NULL,
                last_login_ip VARCHAR(50),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sys_role (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                role_code VARCHAR(50) NOT NULL UNIQUE,
                role_name VARCHAR(50) NOT NULL,
                description VARCHAR(255),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        executeSafely("ALTER TABLE sys_user ADD COLUMN role_id BIGINT NULL");
        executeSafely("ALTER TABLE sys_user ADD COLUMN is_admin TINYINT DEFAULT 0");
        executeSafely("ALTER TABLE sys_user ADD COLUMN global_search_enabled TINYINT DEFAULT 0");
        executeSafely("ALTER TABLE sys_user ADD COLUMN account_type VARCHAR(20) DEFAULT 'NORMAL'");
        executeSafely("ALTER TABLE sys_user ADD COLUMN expire_at DATETIME NULL");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sys_project (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                customer_name VARCHAR(100),
                project_group VARCHAR(100),
                project_name VARCHAR(100) NOT NULL UNIQUE,
                project_code VARCHAR(100) NOT NULL,
                parent_project_code VARCHAR(100),
                project_level VARCHAR(32) DEFAULT 'PROJECT',
                description VARCHAR(255),
                reminder_enabled TINYINT DEFAULT 1,
                remind_after_days INT DEFAULT 7,
                active TINYINT DEFAULT 1,
                is_active TINYINT DEFAULT 1,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        executeSafely("ALTER TABLE sys_project ADD COLUMN customer_name VARCHAR(100)");
        executeSafely("ALTER TABLE sys_project ADD COLUMN project_group VARCHAR(100)");
        executeSafely("ALTER TABLE sys_project ADD COLUMN parent_project_code VARCHAR(100)");
        executeSafely("ALTER TABLE sys_project ADD COLUMN project_level VARCHAR(32) DEFAULT 'PROJECT'");
        executeSafely("ALTER TABLE sys_project ADD COLUMN is_active TINYINT DEFAULT 1");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sys_user_project (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                user_id BIGINT NOT NULL,
                project_id BIGINT NOT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                UNIQUE KEY uk_user_project (user_id, project_id)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_issue (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                issue_no VARCHAR(64),
                project_id BIGINT NOT NULL,
                source VARCHAR(100),
                source_type VARCHAR(32) DEFAULT 'MANUAL',
                source_batch_id BIGINT NULL,
                source_sheet VARCHAR(100),
                source_row_number INT,
                reporter_name VARCHAR(100),
                received_at DATETIME,
                item_title TEXT,
                category_path VARCHAR(255),
                building_name VARCHAR(100),
                floor_name VARCHAR(100),
                area_name VARCHAR(100),
                system_type VARCHAR(100),
                device_point VARCHAR(100),
                found_at DATETIME,
                description TEXT NOT NULL,
                impact_scope VARCHAR(255),
                severity VARCHAR(100),
                priority VARCHAR(100),
                current_status VARCHAR(32) DEFAULT 'OPEN',
                closure_status VARCHAR(32) DEFAULT 'OPEN',
                owner_name VARCHAR(100),
                latest_progress TEXT,
                completion_status VARCHAR(255),
                completed_at DATETIME NULL,
                notes TEXT,
                internal_conclusion TEXT,
                customer_feedback TEXT,
                cause_category VARCHAR(255),
                cause_detail TEXT,
                preventive_action TEXT,
                follow_up_action TEXT,
                reuse_tags VARCHAR(500),
                knowledge_included TINYINT DEFAULT 1,
                raw_snapshot LONGTEXT,
                dedupe_key VARCHAR(255),
                reminder_enabled TINYINT DEFAULT 1,
                remind_after_days INT DEFAULT 7,
                last_reminded_at DATETIME NULL,
                created_by BIGINT NULL,
                updated_by BIGINT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_issue_project (project_id),
                KEY idx_issue_status (current_status, closure_status),
                KEY idx_issue_received_at (received_at),
                KEY idx_issue_found_at (found_at),
                UNIQUE KEY uk_issue_dedupe (dedupe_key)
            )
            """);
        executeSafely("ALTER TABLE ops_issue ADD COLUMN issue_no VARCHAR(64)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN source_type VARCHAR(32) DEFAULT 'MANUAL'");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN source_batch_id BIGINT NULL");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN reporter_name VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN received_at DATETIME");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN item_title TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN building_name VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN floor_name VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN area_name VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN system_type VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN device_point VARCHAR(100)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN completion_status VARCHAR(255)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN internal_conclusion TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN customer_feedback TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN cause_category VARCHAR(255)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN cause_detail TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN preventive_action TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN follow_up_action TEXT");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN reuse_tags VARCHAR(500)");
        executeSafely("ALTER TABLE ops_issue ADD COLUMN knowledge_included TINYINT DEFAULT 1");
        executeSafely("ALTER TABLE ops_issue ADD INDEX idx_issue_received_at (received_at)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_issue_record (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                issue_id BIGINT NOT NULL,
                action_type VARCHAR(50),
                from_status VARCHAR(32),
                to_status VARCHAR(32),
                content TEXT,
                operator_name VARCHAR(100),
                operate_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_issue_record_issue (issue_id, operate_time)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_issue_attachment (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                issue_id BIGINT NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                file_type VARCHAR(100),
                file_size BIGINT,
                file_path VARCHAR(500) NOT NULL,
                uploaded_by BIGINT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted_flag TINYINT DEFAULT 0,
                KEY idx_attachment_issue (issue_id, created_at)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_import_batch (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                original_file_name VARCHAR(255) NOT NULL,
                batch_status VARCHAR(50) NOT NULL,
                total_rows INT DEFAULT 0,
                review_rows INT DEFAULT 0,
                committed_rows INT DEFAULT 0,
                skipped_rows INT DEFAULT 0,
                summary_json LONGTEXT,
                created_by BIGINT NULL,
                created_by_name VARCHAR(100),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_import_row_review (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                batch_id BIGINT NOT NULL,
                sheet_name VARCHAR(100),
                row_no INT NOT NULL,
                row_type VARCHAR(50),
                review_status VARCHAR(50),
                review_message VARCHAR(255),
                commit_status VARCHAR(50) DEFAULT 'PENDING',
                normalized_data LONGTEXT,
                raw_data LONGTEXT,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_import_row_batch (batch_id, row_no)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_project_contact (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                project_id BIGINT NOT NULL,
                position_title VARCHAR(100),
                contact_name VARCHAR(100),
                contact_info VARCHAR(100),
                responsibility VARCHAR(255),
                notes VARCHAR(255),
                source_sheet VARCHAR(100),
                source_row_number INT,
                raw_snapshot LONGTEXT,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_project_warranty (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                project_id BIGINT NOT NULL,
                contract_type VARCHAR(100),
                start_at DATETIME NULL,
                end_at DATETIME NULL,
                service_scope VARCHAR(500),
                contract_signed_at DATETIME NULL,
                acceptance_at DATETIME NULL,
                warranty_term VARCHAR(100),
                expire_at DATETIME NULL,
                file_name VARCHAR(255),
                file_type VARCHAR(100),
                file_size BIGINT,
                file_path VARCHAR(500),
                notes VARCHAR(255),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0
            )
            """);
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN contract_type VARCHAR(100)");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN start_at DATETIME NULL");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN end_at DATETIME NULL");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN service_scope VARCHAR(500)");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN file_name VARCHAR(255)");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN file_type VARCHAR(100)");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN file_size BIGINT");
        executeSafely("ALTER TABLE ops_project_warranty ADD COLUMN file_path VARCHAR(500)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_visit (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                project_id BIGINT NOT NULL,
                visit_no VARCHAR(64),
                visit_title VARCHAR(255),
                service_period VARCHAR(100),
                service_year INT,
                service_quarter INT,
                planned_start_at DATETIME NULL,
                planned_end_at DATETIME NULL,
                actual_start_at DATETIME NULL,
                actual_end_at DATETIME NULL,
                status VARCHAR(32) DEFAULT 'PLANNED',
                summary TEXT,
                conclusion TEXT,
                source_file_path VARCHAR(1000),
                source_sheet VARCHAR(100),
                source_row_number INT,
                source_hash VARCHAR(128),
                created_by BIGINT NULL,
                updated_by BIGINT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_visit_project (project_id),
                KEY idx_maintenance_visit_status (status),
                KEY idx_maintenance_visit_period (service_year, service_quarter),
                KEY idx_maintenance_visit_source_hash (source_hash)
            )
            """);
        executeSafely("ALTER TABLE ops_maintenance_visit ADD COLUMN source_file_path VARCHAR(1000)");
        executeSafely("ALTER TABLE ops_maintenance_visit ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_maintenance_visit ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_maintenance_visit ADD COLUMN source_hash VARCHAR(128)");
        executeSafely("ALTER TABLE ops_maintenance_visit ADD INDEX idx_maintenance_visit_source_hash (source_hash)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_assignment (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                visit_id BIGINT NOT NULL,
                scheduled_at DATETIME NULL,
                floor_name VARCHAR(100),
                task_item VARCHAR(500),
                owner_name VARCHAR(100),
                status VARCHAR(32) DEFAULT 'PENDING',
                notes VARCHAR(500),
                source_file_path VARCHAR(1000),
                source_sheet VARCHAR(100),
                source_row_number INT,
                source_hash VARCHAR(128),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_assignment_visit (visit_id, scheduled_at),
                KEY idx_maintenance_assignment_source_hash (source_hash)
            )
            """);
        executeSafely("ALTER TABLE ops_maintenance_assignment ADD COLUMN source_file_path VARCHAR(1000)");
        executeSafely("ALTER TABLE ops_maintenance_assignment ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_maintenance_assignment ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_maintenance_assignment ADD COLUMN source_hash VARCHAR(128)");
        executeSafely("ALTER TABLE ops_maintenance_assignment ADD INDEX idx_maintenance_assignment_source_hash (source_hash)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_personnel (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                visit_id BIGINT NOT NULL,
                person_name VARCHAR(100),
                phone VARCHAR(50),
                role_name VARCHAR(100),
                notes VARCHAR(500),
                source_file_path VARCHAR(1000),
                source_sheet VARCHAR(100),
                source_row_number INT,
                source_hash VARCHAR(128),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_personnel_visit (visit_id),
                KEY idx_maintenance_personnel_source_hash (source_hash)
            )
            """);
        executeSafely("ALTER TABLE ops_maintenance_personnel ADD COLUMN source_file_path VARCHAR(1000)");
        executeSafely("ALTER TABLE ops_maintenance_personnel ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_maintenance_personnel ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_maintenance_personnel ADD COLUMN source_hash VARCHAR(128)");
        executeSafely("ALTER TABLE ops_maintenance_personnel ADD INDEX idx_maintenance_personnel_source_hash (source_hash)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_finding (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                visit_id BIGINT NOT NULL,
                floor_name VARCHAR(100),
                area_name VARCHAR(255),
                issue_description TEXT,
                handling_result TEXT,
                completion_status VARCHAR(100),
                cause_analysis TEXT,
                follow_up_action TEXT,
                quote_required TINYINT DEFAULT 0,
                knowledge_included TINYINT DEFAULT 1,
                found_at DATETIME NULL,
                source_file_path VARCHAR(1000),
                source_sheet VARCHAR(100),
                source_row_number INT,
                source_hash VARCHAR(128),
                created_by BIGINT NULL,
                updated_by BIGINT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_finding_visit (visit_id),
                KEY idx_maintenance_finding_found_at (found_at),
                KEY idx_maintenance_finding_source_hash (source_hash)
            )
            """);
        executeSafely("ALTER TABLE ops_maintenance_finding ADD COLUMN source_file_path VARCHAR(1000)");
        executeSafely("ALTER TABLE ops_maintenance_finding ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_maintenance_finding ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_maintenance_finding ADD COLUMN source_hash VARCHAR(128)");
        executeSafely("ALTER TABLE ops_maintenance_finding ADD INDEX idx_maintenance_finding_source_hash (source_hash)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_quote_item (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                visit_id BIGINT NOT NULL,
                area_name VARCHAR(255),
                item_name VARCHAR(255),
                quantity DECIMAL(12, 2) DEFAULT 0,
                unit_name VARCHAR(50),
                unit_price DECIMAL(12, 2) DEFAULT 0,
                amount DECIMAL(12, 2) DEFAULT 0,
                notes VARCHAR(500),
                source_file_path VARCHAR(1000),
                source_sheet VARCHAR(100),
                source_row_number INT,
                source_hash VARCHAR(128),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_quote_visit (visit_id),
                KEY idx_maintenance_quote_source_hash (source_hash)
            )
            """);
        executeSafely("ALTER TABLE ops_maintenance_quote_item ADD COLUMN source_file_path VARCHAR(1000)");
        executeSafely("ALTER TABLE ops_maintenance_quote_item ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_maintenance_quote_item ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_maintenance_quote_item ADD COLUMN source_hash VARCHAR(128)");
        executeSafely("ALTER TABLE ops_maintenance_quote_item ADD INDEX idx_maintenance_quote_source_hash (source_hash)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_attachment (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                finding_id BIGINT NOT NULL,
                visit_id BIGINT NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                file_type VARCHAR(100),
                file_size BIGINT,
                file_path VARCHAR(500) NOT NULL,
                uploaded_by BIGINT NULL,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                deleted_flag TINYINT DEFAULT 0,
                KEY idx_maintenance_attachment_finding (finding_id, created_at),
                KEY idx_maintenance_attachment_visit (visit_id, created_at)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_maintenance_source_file (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                visit_id BIGINT NULL,
                project_name VARCHAR(100),
                file_type VARCHAR(32),
                file_name VARCHAR(255),
                file_path VARCHAR(1000),
                zip_entry_path VARCHAR(1000),
                import_status VARCHAR(32) DEFAULT 'IMPORTED',
                message VARCHAR(1000),
                source_hash VARCHAR(128),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                KEY idx_maintenance_source_visit (visit_id),
                KEY idx_maintenance_source_hash (source_hash)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_knowledge (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                issue_id BIGINT NULL,
                project_id BIGINT NULL,
                source_type VARCHAR(32) DEFAULT 'ISSUE_LEDGER',
                source_ref_type VARCHAR(32),
                source_ref_id BIGINT NULL,
                source_name VARCHAR(255),
                source_sheet VARCHAR(100),
                source_row_number INT,
                title VARCHAR(255) NOT NULL,
                fault_code VARCHAR(32) DEFAULT 'OTHER',
                symptom_summary TEXT,
                cause_summary TEXT,
                solution_summary TEXT,
                prevention_summary TEXT,
                tags VARCHAR(500),
                status VARCHAR(32) DEFAULT 'PUBLISHED',
                quality_score INT DEFAULT 0,
                quality_status VARCHAR(32) DEFAULT 'NEEDS_REVIEW',
                quality_issues VARCHAR(500),
                created_by BIGINT NULL,
                updated_by BIGINT NULL,
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                UNIQUE KEY uk_knowledge_issue (issue_id),
                KEY idx_knowledge_project (project_id),
                KEY idx_knowledge_fault (fault_code),
                KEY idx_knowledge_status (status),
                KEY idx_knowledge_update_time (update_time)
            )
            """);
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN fault_code VARCHAR(32) DEFAULT 'OTHER'");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN status VARCHAR(32) DEFAULT 'PUBLISHED'");
        executeSafely("ALTER TABLE ops_knowledge MODIFY COLUMN issue_id BIGINT NULL");
        executeSafely("ALTER TABLE ops_knowledge MODIFY COLUMN project_id BIGINT NULL");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_type VARCHAR(32) DEFAULT 'ISSUE_LEDGER'");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_ref_type VARCHAR(32)");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_ref_id BIGINT NULL");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_name VARCHAR(255)");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_sheet VARCHAR(100)");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN source_row_number INT");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN quality_score INT DEFAULT 0");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN quality_status VARCHAR(32) DEFAULT 'NEEDS_REVIEW'");
        executeSafely("ALTER TABLE ops_knowledge ADD COLUMN quality_issues VARCHAR(500)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_project (project_id)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_fault (fault_code)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_status (status)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_quality_status (quality_status)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_source (source_type, source_name)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_source_row (source_type, source_name, source_sheet, source_row_number)");
        executeSafely("ALTER TABLE ops_knowledge ADD INDEX idx_knowledge_source_ref (source_ref_type, source_ref_id)");
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS ops_category_dict (
                id BIGINT PRIMARY KEY AUTO_INCREMENT,
                level1 VARCHAR(100) NOT NULL,
                level2 VARCHAR(100),
                level3 VARCHAR(100),
                problem_description VARCHAR(255),
                example_case VARCHAR(255),
                create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                deleted TINYINT DEFAULT 0,
                UNIQUE KEY uk_category (level1, level2, level3)
            )
            """);
    }

    private void ensureCustomerProjectSeedData() {
        ensureCustomer("ABB", "CUS-ABB");
        ensureCustomer("BP", "CUS-BP");
        ensureCustomer("欧莱雅", "CUS-LOREAL");
        ensureCustomer("百威", "CUS-BUDWEISER");
        ensureCustomer("商飞", "CUS-COMAC");
        ensureCustomer("星巴克", "CUS-STARBUCKS");

        ensureProject("ABB", "ABB", "上海ABB-KQ", "PRJ-SHANGHAI-ABB-KQ", "CUS-ABB", "区域：办公楼/车间；设备类型：照明；状态：运维中", 1);
        ensureProject("ABB", "ABB", "上海ABB-P6", "PRJ-SHANGHAI-ABB-P6", "CUS-ABB", "区域：办公楼/车间、2#实验室；设备类型：照明、空调、窗帘/外遮阳；状态：运维中", 1);
        ensureProject("BP", "BP", "上海BP办公楼", "PRJ-SHANGHAI-BP-OFFICE", "CUS-BP", "区域：办公区；设备类型：照明；状态：运维中", 1);
        ensureProject("欧莱雅", "欧莱雅", "上海欧莱雅越洋", "PRJ-SHANGHAI-LOREAL-YUEYANG", "CUS-LOREAL", "区域：办公楼；设备类型：照明；状态：运维中", 1);
        ensureProject("欧莱雅", "欧莱雅", "上海欧莱雅静华", "PRJ-SHANGHAI-LOREAL-JINGHUA", "CUS-LOREAL", "区域：办公楼；设备类型：照明；状态：运维中", 1);
        ensureProject("百威", "百威", "南昌百威工厂", "PRJ-NANCHANG-BUDWEISER-FACTORY", "CUS-BUDWEISER", "区域：厂区；设备类型：照明；状态：运维中", 1);
        ensureProject("百威", "百威", "佛山百威工厂", "PRJ-FOSHAN-BUDWEISER-FACTORY", "CUS-BUDWEISER", "区域：厂区；设备类型：照明；状态：运维中", 1);
        ensureProject("商飞", "商飞", "上海商飞铺材二期", "PRJ-SHANGHAI-COMAC-PUCAI-P2", "CUS-COMAC", "区域：车间；设备类型：照明；状态：运维中", 1);
        ensureProject("星巴克", "星巴克", "昆山星巴克办公楼", "PRJ-KUNSHAN-STARBUCKS-OFFICE", "CUS-STARBUCKS", "区域：办公楼/车间；设备类型：照明；状态：历史项目", 0);
    }

    private void ensureCustomer(String customerName, String projectCode) {
        jdbcTemplate.update("""
            INSERT INTO sys_project (
                customer_name, project_group, project_name, project_code, parent_project_code,
                project_level, description, reminder_enabled, remind_after_days, active, is_active, deleted
            ) VALUES (?, ?, ?, ?, NULL, 'CUSTOMER', ?, 1, 7, 1, 1, 0)
            ON DUPLICATE KEY UPDATE
                customer_name = VALUES(customer_name),
                project_group = VALUES(project_group),
                project_code = VALUES(project_code),
                parent_project_code = VALUES(parent_project_code),
                project_level = VALUES(project_level),
                description = VALUES(description),
                reminder_enabled = VALUES(reminder_enabled),
                remind_after_days = VALUES(remind_after_days),
                active = VALUES(active),
                is_active = VALUES(is_active),
                deleted = 0
            """, customerName, customerName, customerName, projectCode, "客户分类库初始客户");
    }

    private void ensureProject(String customerName, String projectGroup, String projectName, String projectCode, String parentProjectCode, String description, int isActive) {
        jdbcTemplate.update("""
            INSERT INTO sys_project (
                customer_name, project_group, project_name, project_code, parent_project_code,
                project_level, description, reminder_enabled, remind_after_days, active, is_active, deleted
            ) VALUES (?, ?, ?, ?, ?, 'PROJECT', ?, 1, 7, ?, ?, 0)
            ON DUPLICATE KEY UPDATE
                customer_name = VALUES(customer_name),
                project_group = VALUES(project_group),
                project_code = VALUES(project_code),
                parent_project_code = VALUES(parent_project_code),
                project_level = VALUES(project_level),
                description = VALUES(description),
                reminder_enabled = VALUES(reminder_enabled),
                remind_after_days = VALUES(remind_after_days),
                active = VALUES(active),
                is_active = VALUES(is_active),
                deleted = 0
            """, customerName, projectGroup, projectName, projectCode, parentProjectCode, description, isActive, isActive);
    }

    private void applyHistoryDataGovernance() {
        executeSafely("""
            UPDATE ops_issue
            SET category_path = CASE
                    WHEN category_path IS NOT NULL AND TRIM(category_path) <> '' THEN category_path
                    WHEN CONCAT_WS(' ', item_title, description, latest_progress) REGEXP '离线|超时|连接失败|网关|网络|485|Socket|MQTT|路由|交换机' THEN '通讯/网络问题'
                    WHEN CONCAT_WS(' ', item_title, description, latest_progress) REGEXP '定时|联动|人感|光感|策略|常亮|常关|不灭|不亮' THEN '策略/联动/定时问题'
                    WHEN CONCAT_WS(' ', item_title, description, latest_progress) REGEXP '导入|统计|报表|数据|能耗|Dashboard|Excel|CSV' THEN '数据/统计问题'
                    WHEN CONCAT_WS(' ', item_title, description, latest_progress) REGEXP '登录|页面|接口|500|404|服务|平台|保存失败' THEN '软件平台/服务问题'
                    WHEN CONCAT_WS(' ', item_title, description, latest_progress) REGEXP '新增|需求|调整|优化|改成|增加' THEN '需求变更'
                    ELSE '待确认问题'
                END,
                cause_category = CASE
                    WHEN cause_category IS NOT NULL AND TRIM(cause_category) <> '' THEN cause_category
                    ELSE '原因待确认'
                END,
                source = CASE
                    WHEN source IS NOT NULL AND TRIM(source) <> '' THEN source
                    WHEN source_type = 'EXCEL' OR source_batch_id IS NOT NULL THEN 'Excel/CSV 导入'
                    ELSE '手动录入'
                END,
                priority = CASE
                    WHEN priority IS NOT NULL AND TRIM(priority) <> '' THEN priority
                    ELSE '中'
                END,
                owner_name = CASE
                    WHEN owner_name IS NOT NULL AND TRIM(owner_name) <> '' THEN owner_name
                    ELSE '未分配'
                END,
                building_name = CASE
                    WHEN building_name IS NOT NULL AND TRIM(building_name) <> '' THEN building_name
                    ELSE '未确认'
                END,
                floor_name = CASE
                    WHEN floor_name IS NOT NULL AND TRIM(floor_name) <> '' THEN floor_name
                    ELSE '未确认'
                END,
                area_name = CASE
                    WHEN area_name IS NOT NULL AND TRIM(area_name) <> '' THEN area_name
                    ELSE '未确认'
                END,
                system_type = CASE
                    WHEN system_type IS NOT NULL AND TRIM(system_type) <> '' THEN system_type
                    ELSE '未确认'
                END,
                device_point = CASE
                    WHEN device_point IS NOT NULL AND TRIM(device_point) <> '' THEN device_point
                    ELSE '未确认'
                END
            WHERE deleted = 0
            """);
    }

    private void applyProjectMasterDataGovernance() {
        executeSafely("""
            UPDATE sys_project
            SET customer_name = 'ABB',
                project_group = 'ABB',
                project_code = CASE
                    WHEN project_name = '上海ABB-P6' THEN project_code
                    ELSE project_code
                END
            WHERE deleted = 0
              AND UPPER(REPLACE(REPLACE(project_name, ' ', ''), '-', '')) LIKE '%ABB%P6%'
            """);
        executeSafely("""
            UPDATE sys_project p
            SET project_name = '上海ABB-P6',
                project_code = 'PRJ-SHANGHAI-ABB-P6'
            WHERE p.deleted = 0
              AND UPPER(REPLACE(REPLACE(p.project_name, ' ', ''), '-', '')) LIKE '%ABB%P6%'
              AND NOT EXISTS (
                  SELECT 1 FROM (SELECT id FROM sys_project WHERE project_name = '上海ABB-P6' AND deleted = 0) existing
                  WHERE existing.id <> p.id
              )
            """);
        executeSafely("""
            UPDATE sys_project
            SET customer_name = '上海交通大学',
                project_group = '上海交大'
            WHERE deleted = 0
              AND (project_name LIKE '%上海交通大学%' OR project_name LIKE '%上海交大%' OR project_name LIKE '%交大%')
            """);
        executeSafely("""
            UPDATE sys_project p
            SET project_name = '上海交通大学闵行校区',
                project_code = 'PRJ-SJTU-MINHANG'
            WHERE p.deleted = 0
              AND (p.project_name LIKE '%上海交通大学%' OR p.project_name LIKE '%上海交大%' OR p.project_name LIKE '%交大%')
              AND (p.project_name LIKE '%闵行%' OR p.project_name LIKE '%校区%' OR p.project_name LIKE '%交大%')
              AND NOT EXISTS (
                  SELECT 1 FROM (SELECT id FROM sys_project WHERE project_name = '上海交通大学闵行校区' AND deleted = 0) existing
                  WHERE existing.id <> p.id
              )
            """);
    }

    private void ensureDefaultRoles() {
        ensureRole("ADMIN", "系统管理员", "拥有系统全部管理权限，可维护用户、角色和项目授权");
        ensureRole("PROJECT_OWNER", "项目负责人", "负责项目数据查看、问题跟进和项目范围协同");
        ensureRole("ENGINEER", "工程师", "处理授权项目内的问题、记录进展和上传图片");
        ensureRole("TEMP_WORKER", "临时工人", "临时账号角色，只能在有效期内新建问题、上传图片并查看自己相关问题");
        Long engineerRoleId = roleIdByCode("ENGINEER");
        if (engineerRoleId != null) {
            jdbcTemplate.update("UPDATE sys_user SET role_id = ? WHERE role_id IS NULL AND (is_admin IS NULL OR is_admin = 0)", engineerRoleId);
        }
        jdbcTemplate.update("UPDATE sys_user SET account_type = 'NORMAL' WHERE account_type IS NULL OR account_type = ''");
    }

    private void ensureRole(String code, String name, String description) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_code = ? AND deleted = 0", Long.class, code);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                "INSERT INTO sys_role (role_code, role_name, description, deleted) VALUES (?, ?, ?, 0)",
                code,
                name,
                description
            );
        } else {
            jdbcTemplate.update("UPDATE sys_role SET role_name = ? WHERE role_code = ? AND deleted = 0", name, code);
        }
    }

    private Long roleIdByCode(String code) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM sys_role WHERE role_code = ? AND deleted = 0 LIMIT 1", (rs, rowNum) -> rs.getLong("id"), code);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private void ensureAdminUserWithRole() {
        Long adminRoleId = roleIdByCode("ADMIN");
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND deleted = 0", Long.class);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, email, status, role_id, is_admin, global_search_enabled, account_type, deleted) VALUES (?, ?, ?, ?, 1, ?, 1, 1, 'NORMAL', 0)",
                "admin",
                passwordEncoder.encode("admin123"),
                "系统管理员",
                "admin@lontri.local",
                adminRoleId
            );
        } else {
            jdbcTemplate.update("UPDATE sys_user SET role_id = ?, is_admin = 1, global_search_enabled = 1, account_type = 'NORMAL' WHERE username = 'admin'", adminRoleId);
        }
    }

    private void ensureAdminUser() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = 'admin' AND deleted = 0", Long.class);
        if (count == null || count == 0) {
            jdbcTemplate.update(
                "INSERT INTO sys_user (username, password, real_name, email, status, is_admin, global_search_enabled, deleted) VALUES (?, ?, ?, ?, 1, 1, 1, 0)",
                "admin",
                passwordEncoder.encode("admin123"),
                "系统管理员",
                "admin@lontri.local"
            );
        } else {
            jdbcTemplate.update("UPDATE sys_user SET is_admin = 1, global_search_enabled = 1 WHERE username = 'admin'");
        }
    }

    private void executeSafely(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ignored) {
            log.debug("Skip schema patch: {}", sql);
        }
    }
}
