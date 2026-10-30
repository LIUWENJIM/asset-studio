-- 用户身份：匿名 uid Cookie 对应的用户记录
CREATE TABLE app_user (
    id           VARCHAR(36)  NOT NULL,
    display_name VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);

-- 模板：只读种子数据，接口按分类筛选
CREATE TABLE template (
    id                   VARCHAR(64)  NOT NULL,
    title                VARCHAR(128) NOT NULL,
    category             VARCHAR(64)  NOT NULL,
    cover_url            VARCHAR(255) NOT NULL,
    description          VARCHAR(512) NOT NULL,
    required_image_count INT          NOT NULL,
    accepted_mime_types  VARCHAR(255) NOT NULL,
    max_file_size_bytes  BIGINT       NOT NULL,
    sort_order           INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 素材：上传成功后落库，文件存本地目录
CREATE TABLE asset (
    id            VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(36)  NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_path   VARCHAR(512) NOT NULL,
    mime_type     VARCHAR(64)  NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_asset_user ON asset (user_id);

-- 制作任务：status/stage 分离，结果与失败原因同表
CREATE TABLE job (
    id                 VARCHAR(36)  NOT NULL,
    user_id            VARCHAR(36)  NOT NULL,
    template_id        VARCHAR(64)  NOT NULL,
    template_title     VARCHAR(128) NOT NULL,
    template_cover_url VARCHAR(255) NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    stage              VARCHAR(16),
    result_url         VARCHAR(512),
    failure_stage      VARCHAR(16),
    failure_reason     VARCHAR(512),
    created_at         TIMESTAMP    NOT NULL,
    updated_at         TIMESTAMP    NOT NULL,
    finished_at        TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_job_user_created ON job (user_id, created_at);
CREATE INDEX idx_job_status ON job (status);

-- 任务与素材的有序关联
CREATE TABLE job_asset (
    job_id   VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    position INT         NOT NULL,
    PRIMARY KEY (job_id, position)
);