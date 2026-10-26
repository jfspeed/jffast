-- =============================================
-- 租户隔离示例表（jffast-demo）
-- =============================================
--
-- 必须带 tenant_id 且不进租户 ignore-tables，否则看不到隔离效果。
-- Base 版租户拦截仅预留（默认关闭），本表在 Base 下表现为普通 CRUD；
-- 开启租户拦截后自动按当前租户隔离。
--
-- 执行：mysql -u<user> -p <db> < sql/demo_tenant_example.sql
-- （可选文件，不需要示例模块的部署可以跳过）
CREATE TABLE IF NOT EXISTS `demo_tenant_example` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID（Pro 租户拦截自动填充与过滤）',
    `title`           VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '标题',
    `remark`          VARCHAR(500)    NULL     COMMENT '备注',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_tenant_id` (`tenant_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='租户隔离示例表';
