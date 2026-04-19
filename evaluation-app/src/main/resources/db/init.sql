-- ====================================================================
-- ai-code-change-evaluation 数据库初始化脚本
-- 数据库：PostgreSQL 14+
-- 执行方式：以 superuser 身份连接默认库（postgres）后运行本文件
-- ====================================================================

-- ====================================================================
-- 步骤 1：创建数据库（数据库已存在时跳过）
-- ====================================================================
-- PostgreSQL 不支持 CREATE DATABASE IF NOT EXISTS，通过元命令变通实现
SELECT 'CREATE DATABASE ai_evaluation
    WITH ENCODING    = ''UTF8''
         LC_COLLATE  = ''en_US.UTF-8''
         LC_CTYPE    = ''en_US.UTF-8''
         TEMPLATE    = template0'
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = 'ai_evaluation'
)\gexec

-- 切换到目标数据库（psql 客户端命令）
\c ai_evaluation

-- ====================================================================
-- 步骤 2：建表
-- ====================================================================

-- 仓库注册信息
CREATE TABLE IF NOT EXISTS t_repository
(
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(256) NOT NULL,
    clone_url   TEXT         NOT NULL,
    branch      VARCHAR(128) NOT NULL DEFAULT 'main',
    local_path  TEXT         NOT NULL,
    status      SMALLINT     NOT NULL DEFAULT 1,         -- 1:ACTIVE 0:INACTIVE
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE t_repository IS '仓库注册信息';

-- 索引任务记录
CREATE TABLE IF NOT EXISTS t_index_task
(
    id             BIGSERIAL PRIMARY KEY,
    repo_id        VARCHAR(64)  NOT NULL,
    task_type      VARCHAR(16)  NOT NULL,                    -- FULL / INCREMENTAL
    trigger_commit VARCHAR(64),
    status         SMALLINT     NOT NULL DEFAULT 0,         -- 0:PENDING 1:RUNNING 2:SUCCESS 3:FAIL
    start_time     TIMESTAMP,
    finish_time    TIMESTAMP,
    error_msg      TEXT,
    create_time    TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time    TIMESTAMP    NOT NULL DEFAULT NOW()
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
    status             SMALLINT     NOT NULL DEFAULT 1,           -- 1:PROCESSED 2:SKIPPED
    create_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time        TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (repo_id, commit_hash)
);

COMMENT ON TABLE t_commit_record IS '已处理提交记录，用于增量索引幂等控制';
CREATE INDEX IF NOT EXISTS idx_commit_record_repo_hash ON t_commit_record (repo_id, commit_hash);

-- 解析失败记录
CREATE TABLE IF NOT EXISTS t_parse_error
(
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT       NOT NULL,
    file_path   TEXT         NOT NULL,
    error_type  VARCHAR(64)  NOT NULL, -- PARSE_ERROR / IO_ERROR 等
    error_msg   TEXT,
    status      SMALLINT     NOT NULL DEFAULT 0,            -- 0:UNRESOLVED 1:RESOLVED
    create_time TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE t_parse_error IS '文件 AST 解析失败记录';
CREATE INDEX IF NOT EXISTS idx_parse_error_task_id ON t_parse_error (task_id);
