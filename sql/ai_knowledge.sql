-- ============================================================
-- AI 知识库模块（jffast-ai）建表脚本
-- 包含：知识库 / 文档 / 分块 / 对话 / 消息
--
-- 说明：
--   1. 本模块只负责「AI 数据的存储与管理」，不包含大模型对接能力。
--      实际调用 OpenAI / 智谱 / DeepSeek 等模型的能力需自行扩展。
--   2. 依赖 sys_menu 表，请先执行 system_sql.sql 与 system_init_data.sql。
--   3. 字段与 cn.jfspeed.ai.entity.* 一一对应：
--      - 项目未启用逻辑删除，故不设 is_deleted 列
--      - tenantId / deptId 在 BaseEntity 中为 @TableField(exist = false)：实体不映射；
--        多租户开启时由 Pro 拦截器在 SQL 层注入 tenant_id 条件（下表已带该列）
--   4. 菜单 ID 段占用 9000-9099，避开 system(1000+)/flow(3000+) 等既有区段，
--      新增菜单请顺延本段，勿与 1601 等既有 ID 冲突
-- ============================================================

-- ----------------------------
-- AI 知识库（对应 AiKnowledgeBase）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_knowledge_base` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID',
    `name`            VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '知识库名称',
    `code`            VARCHAR(100)    NULL     COMMENT '知识库编码',
    `description`     VARCHAR(500)    NULL     COMMENT '描述',
    `embedding_model` VARCHAR(100)    NULL     COMMENT '向量模型',
    `chunk_size`      INT             NOT NULL DEFAULT 500 COMMENT '分块大小',
    `chunk_overlap`   INT             NOT NULL DEFAULT 50 COMMENT '分块重叠',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 知识库';

-- ============================================================
-- ⚠️ 幂等补列：`document_count` / `chunk_count` / `is_deleted`
--
-- 单一事实源：
--   `ai_knowledge_base` 的 **CREATE TABLE 只存在于本文件**。
--   不再可能出现「谁后执行谁赢」。
--
-- 为什么要保留下面这 3 列的幂等补列：
--   · 旧库可能由旧版脚本建出（只有 document_count/chunk_count/is_deleted，
--     没有 code/embedding_model/chunk_size/chunk_overlap），本文件的 CREATE 会整表跳过；
--   · 新库由本文件建出后，缺的正是上面这 3 列。
--   两个方向都由下面的幂等补列兜住。
--
-- 幂等性：information_schema 判断 + 动态 SQL（表不存在/列已存在都退化为 DO 0），
-- 保证 `_apply-all.sql` 可反复执行。MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`。
-- ============================================================

-- ai_knowledge_base.document_count
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base'
            AND COLUMN_NAME = 'document_count') = 0,
        'ALTER TABLE `ai_knowledge_base` ADD COLUMN `document_count` INT DEFAULT 0 COMMENT ''文档数量''',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_knowledge_base.chunk_count
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base'
            AND COLUMN_NAME = 'chunk_count') = 0,
        'ALTER TABLE `ai_knowledge_base` ADD COLUMN `chunk_count` INT DEFAULT 0 COMMENT ''文本块数量''',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_knowledge_base.is_deleted
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base'
            AND COLUMN_NAME = 'is_deleted') = 0,
        'ALTER TABLE `ai_knowledge_base` ADD COLUMN `is_deleted` TINYINT DEFAULT 0 COMMENT ''是否删除''',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ----------------------------
-- AI 文档（对应 AiDocument）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_document` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID',
    `kb_id`           BIGINT          NULL     COMMENT '知识库ID',
    `title`           VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '文档标题',
    `content`         LONGTEXT        NULL     COMMENT '文档内容',
    `content_type`    VARCHAR(20)     NOT NULL DEFAULT 'text' COMMENT '内容类型：text/markdown/html/pdf',
    `source_type`     VARCHAR(20)     NOT NULL DEFAULT 'manual' COMMENT '来源类型：manual/file/url/api',
    `source_url`      VARCHAR(500)    NULL     COMMENT '来源URL',
    `file_path`       VARCHAR(500)    NULL     COMMENT '文件路径',
    `chunk_count`     INT             NOT NULL DEFAULT 0 COMMENT '分块数量',
    `token_count`     INT             NOT NULL DEFAULT 0 COMMENT 'Token 数量',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待处理，1-已处理，2-处理失败',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 文档';

-- ----------------------------
-- AI 文档分块（预留表）
-- 当前版本无对应实体，供后续「文档向量化 / 检索」扩展使用，保留定义避免二次建表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_chunk` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID',
    `kb_id`           BIGINT          NULL     COMMENT '知识库ID',
    `doc_id`          BIGINT          NULL     COMMENT '文档ID',
    `content`         TEXT            NOT NULL COMMENT '分块内容',
    `token_count`     INT             NOT NULL DEFAULT 0 COMMENT 'Token 数量',
    `chunk_index`     INT             NOT NULL DEFAULT 0 COMMENT '分块索引',
    `metadata`        TEXT            NULL     COMMENT '元数据',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_doc_id` (`doc_id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 文档分块';

-- ----------------------------
-- AI 对话（对应 AiConversation）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_conversation` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID',
    `user_id`         BIGINT          NULL     COMMENT '用户ID',
    `kb_id`           BIGINT          NULL     COMMENT '知识库ID',
    `title`           VARCHAR(200)    NULL     COMMENT '对话标题',
    `model`           VARCHAR(100)    NULL     COMMENT '模型名称',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_kb_id` (`kb_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 对话';

-- ----------------------------
-- AI 消息（对应 AiMessage）
-- 注意：create_by / update_by / update_time 三列不可省，AiMessage 继承 BaseEntity 会映射它们
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_message` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NULL     COMMENT '租户ID',
    `conversation_id` BIGINT          NULL     COMMENT '对话ID',
    `role`            VARCHAR(20)     NOT NULL DEFAULT 'user' COMMENT '角色：user/assistant/system',
    `content`         LONGTEXT        NULL     COMMENT '消息内容',
    `token_count`     INT             NOT NULL DEFAULT 0 COMMENT 'Token 数量',
    `metadata`        TEXT            NULL     COMMENT '元数据',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_conversation_id` (`conversation_id`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI 消息';

-- ============================================================
-- ⚠️ 幂等补列：租户隔离列 `tenant_id`（AI 五表）
--
-- 背景：
--   开启多租户后，任何带租户上下文的 AI 页面查询都会给 SQL 追加
--   `tenant_id = ?`；缺列即 `Unknown column 'tenant_id'`。
--   `ai_knowledge_base` 由 jffast-ai 的 AiKnowledgeBase 实体复用，
--   而本文件是它的唯一建表事实源，故列一并在此补。
--
-- 幂等性：information_schema 判断 + 动态 SQL（表不存在/列已存在都退化为 DO 0）。
-- MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`。
-- ============================================================

-- ai_knowledge_base.tenant_id
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `ai_knowledge_base` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_knowledge_base 租户列索引
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_knowledge_base'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `ai_knowledge_base` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_document.tenant_id
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `ai_document` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_document 租户列索引
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_document'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `ai_document` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_chunk.tenant_id
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chunk') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chunk'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `ai_chunk` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_chunk 租户列索引
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chunk') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_chunk'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `ai_chunk` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_conversation.tenant_id
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `ai_conversation` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_conversation 租户列索引
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_conversation'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `ai_conversation` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_message.tenant_id
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_message') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_message'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `ai_message` ADD COLUMN `tenant_id` BIGINT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ai_message 租户列索引
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_message') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_message'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `ai_message` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- ============================================================
-- 菜单与权限（ID 段 9000-9099）
-- 纯管理段，避开 1601（系统日志查询）等既有 ID
-- 超级管理员自动拥有全部权限（orRole = ROLE_SUPER_ADMIN），无需 sys_role_menu 授权
-- ============================================================

-- AI 中心目录 + 三个菜单
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(9000, 0, 'AI 中心', 20, 'LAYOUT', 'ai', 'M', 1, '', 0, 'robot', 'AI 中心目录'),
(9001, 9000, '知识库', 1, '/ai/knowledgeBase/index', 'aiKnowledgeBase', 'C', 1, 'ai:knowledge-base:view', 0, 'book', 'AI 知识库管理'),
(9002, 9000, '文档管理', 2, '/ai/document/index', 'aiDocument', 'C', 1, 'ai:document:view', 0, 'file', 'AI 文档管理'),
(9003, 9000, '对话记录', 3, '/ai/conversation/index', 'aiConversation', 'C', 1, 'ai:conversation:view', 0, 'chat', 'AI 对话记录查询');

-- 知识库按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(9011, 9001, '知识库查询', 1, '', NULL, 'F', 1, 'ai:knowledge-base:view', 0, '#', ''),
(9012, 9001, '知识库新增', 2, '', NULL, 'F', 1, 'ai:knowledge-base:add', 0, '#', ''),
(9013, 9001, '知识库修改', 3, '', NULL, 'F', 1, 'ai:knowledge-base:edit', 0, '#', ''),
(9014, 9001, '知识库删除', 4, '', NULL, 'F', 1, 'ai:knowledge-base:delete', 0, '#', '');

-- 文档按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(9021, 9002, '文档查询', 1, '', NULL, 'F', 1, 'ai:document:view', 0, '#', ''),
(9022, 9002, '文档新增', 2, '', NULL, 'F', 1, 'ai:document:add', 0, '#', ''),
(9023, 9002, '文档修改', 3, '', NULL, 'F', 1, 'ai:document:edit', 0, '#', ''),
(9024, 9002, '文档删除', 4, '', NULL, 'F', 1, 'ai:document:delete', 0, '#', '');

-- 对话按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(9031, 9003, '对话查询', 1, '', NULL, 'F', 1, 'ai:conversation:view', 0, '#', ''),
(9032, 9003, '对话新增', 2, '', NULL, 'F', 1, 'ai:conversation:add', 0, '#', ''),
(9033, 9003, '对话删除', 3, '', NULL, 'F', 1, 'ai:conversation:delete', 0, '#', '');

-- ============================================================
-- 默认示例数据：便于登录后直接体验三个页面
-- ============================================================
INSERT IGNORE INTO `ai_knowledge_base` (`id`, `name`, `code`, `description`, `embedding_model`, `chunk_size`, `chunk_overlap`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, '疾风使用手册', 'jffast_manual', '存放疾风框架的使用文档，用于演示知识库与文档管理。', 'text-embedding-3-small', 500, 50, 1, 1, NOW(), 1, NOW());

INSERT IGNORE INTO `ai_document` (`id`, `kb_id`, `title`, `content`, `content_type`, `source_type`, `source_url`, `file_path`, `chunk_count`, `token_count`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 1, '快速开始', '疾风（jffast）基于 Spring Boot 3.5 + JDK 21。执行 mvn clean install -DskipTests 完成构建后进入 jffast-boot 目录，使用 dev profile 启动即可。', 'markdown', 'manual', NULL, NULL, 1, 86, 1, 1, NOW(), 1, NOW());

INSERT IGNORE INTO `ai_conversation` (`id`, `user_id`, `kb_id`, `title`, `model`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 1, 1, '如何启动疾风后端', 'gpt-4o-mini', 1, 1, NOW(), 1, NOW());

INSERT IGNORE INTO `ai_message` (`id`, `conversation_id`, `role`, `content`, `token_count`, `metadata`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 1, 'user', '疾风后端怎么启动？', 12, NULL, 1, NOW(), 1, NOW()),
(2, 1, 'assistant', '执行 mvn clean install -DskipTests 完成构建，然后进入 jffast-boot 目录，用 dev profile 启动：mvn spring-boot:run -Dspring-boot.run.profiles=dev。注意数据源配置只在 application-dev.yml 中，不激活 dev profile 会启动失败。', 88, NULL, 1, NOW(), 1, NOW());
