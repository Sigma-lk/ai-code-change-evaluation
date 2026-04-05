-- ====================================================================
-- ai-code-change-evaluation 数据库初始化脚本
-- 数据库：PostgreSQL 14+
-- ====================================================================

-- 仓库注册信息
CREATE TABLE IF NOT EXISTS t_repository
(
    id         VARCHAR(64) PRIMARY KEY,
    name       VARCHAR(256) NOT NULL,
    clone_url  TEXT         NOT NULL,
    branch     VARCHAR(128) NOT NULL DEFAULT 'main',
    local_path TEXT         NOT NULL,
    status     VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / INACTIVE
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE t_repository IS '仓库注册信息';

-- 索引任务记录
CREATE TABLE IF NOT EXISTS t_index_task
(
    id             BIGSERIAL PRIMARY KEY,
    repo_id        VARCHAR(64)  NOT NULL,
    task_type      VARCHAR(16)  NOT NULL, -- FULL / INCREMENTAL
    trigger_commit VARCHAR(64),
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING', -- PENDING / RUNNING / SUCCESS / FAIL
    started_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    finished_at    TIMESTAMP,
    error_msg      TEXT
);

COMMENT ON TABLE t_index_task IS '全量/增量索引任务记录';
CREATE INDEX IF NOT EXISTS idx_index_task_repo_id ON t_index_task (repo_id);
CREATE INDEX IF NOT EXISTS idx_index_task_status ON t_index_task (status);

-- 已处理提交记录（增量幂等控制）
CREATE TABLE IF NOT EXISTS t_commit_record
(
    id                 BIGSERIAL PRIMARY KEY,
    repo_id            VARCHAR(64)  NOT NULL,
    commit_hash        VARCHAR(64)  NOT NULL,
    author             VARCHAR(256),
    commit_time        TIMESTAMP,
    changed_file_count INTEGER,
    processed_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (repo_id, commit_hash)
);

COMMENT ON TABLE t_commit_record IS '已处理提交记录，用于增量索引幂等控制';
CREATE INDEX IF NOT EXISTS idx_commit_record_repo_hash ON t_commit_record (repo_id, commit_hash);

-- 解析失败记录
CREATE TABLE IF NOT EXISTS t_parse_error
(
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT       NOT NULL,
    file_path  TEXT         NOT NULL,
    error_type VARCHAR(64)  NOT NULL, -- PARSE_ERROR / IO_ERROR 等
    error_msg  TEXT,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE t_parse_error IS '文件 AST 解析失败记录';
CREATE INDEX IF NOT EXISTS idx_parse_error_task_id ON t_parse_error (task_id);
