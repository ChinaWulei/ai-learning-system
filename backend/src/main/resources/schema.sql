CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS learning_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    topic VARCHAR(255) NOT NULL,
    goal VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'CREATED',
    current_stage VARCHAR(100),
    plan_json LONGTEXT,
    resources_json LONGTEXT,
    content_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS learning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    knowledge_point VARCHAR(255),
    action_type VARCHAR(40) NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 0,
    progress DECIMAL(5,2) NOT NULL DEFAULT 0,
    detail_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quiz (
    id VARCHAR(64) PRIMARY KEY,
    task_id BIGINT NOT NULL,
    knowledge_point VARCHAR(255),
    question_type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(20),
    content_json LONGTEXT NOT NULL,
    reference_answer LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS quiz_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    answers_json LONGTEXT NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    feedback_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_profile (
    user_id BIGINT PRIMARY KEY,
    level_name VARCHAR(30) NOT NULL DEFAULT 'beginner',
    mastery_json LONGTEXT,
    weakness_json LONGTEXT,
    preference_json LONGTEXT,
    history_json LONGTEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_execution_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    task_id BIGINT,
    operation VARCHAR(40) NOT NULL,
    trace_json LONGTEXT,
    success BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
