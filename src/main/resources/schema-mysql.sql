CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS problems (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description LONGTEXT NOT NULL,
    time_limit_ms INT NOT NULL,
    memory_limit_mb INT NOT NULL,
    difficulty VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_cases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    problem_id BIGINT NOT NULL,
    input_text LONGTEXT NOT NULL,
    expected_output LONGTEXT NOT NULL,
    is_sample BIT(1) NOT NULL,
    CONSTRAINT fk_test_cases_problem FOREIGN KEY (problem_id) REFERENCES problems (id) ON DELETE CASCADE,
    KEY ix_test_cases_problem_sample (problem_id, is_sample)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS submissions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    language VARCHAR(16) NOT NULL,
    code LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    passed_cases INT NOT NULL DEFAULT 0,
    total_cases INT NOT NULL DEFAULT 0,
    time_used_ms BIGINT,
    memory_used_kb BIGINT,
    error_message VARCHAR(4096),
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    KEY ix_submissions_status (status),
    KEY ix_submissions_history (user_id, created_at DESC, id DESC),
    KEY ix_submissions_problem (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 8 的 ADD COLUMN 没有 IF NOT EXISTS，旧数据库也会运行此脚本。
SET @minioj_submission_alter = CONCAT_WS(', ',
    IF(EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'submissions' AND column_name = 'passed_cases'
    ), NULL, 'ADD COLUMN passed_cases INT NOT NULL DEFAULT 0'),
    IF(EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'submissions' AND column_name = 'total_cases'
    ), NULL, 'ADD COLUMN total_cases INT NOT NULL DEFAULT 0'),
    IF(EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'submissions' AND index_name = 'ix_submissions_problem'
    ), NULL, 'ADD INDEX ix_submissions_problem (problem_id)')
);
SET @minioj_submission_update = IF(@minioj_submission_alter = '', 'SELECT 1',
    CONCAT('ALTER TABLE submissions ', @minioj_submission_alter));
PREPARE minioj_submission_update FROM @minioj_submission_update;
EXECUTE minioj_submission_update;
DEALLOCATE PREPARE minioj_submission_update;

CREATE TABLE IF NOT EXISTS oj_bootstrap (
    seed_name VARCHAR(64) PRIMARY KEY
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
