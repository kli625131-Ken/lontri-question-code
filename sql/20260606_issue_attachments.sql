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
);
