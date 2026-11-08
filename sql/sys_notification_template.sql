-- 通知模板分组表
CREATE TABLE IF NOT EXISTS `sys_notification_template_group` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `name`            VARCHAR(100)    NOT NULL COMMENT '分组名称',
    `code`            VARCHAR(100)    NOT NULL COMMENT '分组编码',
    `description`     VARCHAR(500)    NULL     COMMENT '描述',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `sort`            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    -- 本表**不设** is_deleted：Base 未启用逻辑删除（无 @TableLogic，删除为物理删除），
    -- 建一个没人写、没人过滤的死列只会让"表结构 ↔ 实体"对不上账。
    -- 需要逻辑删除的表：自己加 is_deleted 列 + @TableLogic，两者必须同时到位。
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知模板分组表';

-- 通知模板表
-- tenant_id 语义：0 = 平台默认模板（全局目录，升级时克隆给各启用租户）；
--                非 0 = 租户自有模板，读写由 MyBatis-Plus 租户拦截器按上下文隔离。
-- 唯一键从全局 uk_code 改为 uk_tenant_code：同 code 在不同租户各存一份。
CREATE TABLE IF NOT EXISTS `sys_notification_template` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `tenant_id`       BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID：0=平台默认模板',
    `group_id`        BIGINT          NULL     COMMENT '分组ID',
    `name`            VARCHAR(100)    NOT NULL COMMENT '模板名称',
    `code`            VARCHAR(100)    NOT NULL COMMENT '模板编码',
    `title_template`  VARCHAR(200)    NOT NULL COMMENT '标题模板（支持 ${变量} 占位）',
    `content_template` TEXT           NOT NULL COMMENT '内容模板（支持 ${变量} 占位）',
    `type`            VARCHAR(50)     NOT NULL DEFAULT 'system' COMMENT '通知类型：system-系统通知/biz-业务通知',
    `level`           VARCHAR(20)     NOT NULL DEFAULT 'info' COMMENT '通知级别：info/warning/error',
    `recipient_type`  VARCHAR(50)     NULL     DEFAULT 'all' COMMENT '默认接收人类型：all-全体/role-角色/dept-部门/user-用户',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `remark`          VARCHAR(500)    NULL     COMMENT '备注',
    -- 同上：本表不设 is_deleted（Base 未启用逻辑删除）。
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`),
    KEY `idx_group_id` (`group_id`),
    KEY `idx_tenant_status` (`tenant_id`, `status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知模板表';

-- 存量库升级（幂等）：老版本表没有 tenant_id / 仍是全局 uk_code。
-- MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 才支持），故用 information_schema + 动态 SQL 判断。
SET @has_tenant_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification_template' AND COLUMN_NAME = 'tenant_id');
SET @sql := IF(@has_tenant_col = 0,
    'ALTER TABLE `sys_notification_template` ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT ''租户ID：0=平台默认模板'' AFTER `id`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_old_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification_template' AND INDEX_NAME = 'uk_code');
SET @sql := IF(@has_old_uk > 0,
    'ALTER TABLE `sys_notification_template` DROP INDEX `uk_code`',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_new_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification_template' AND INDEX_NAME = 'uk_tenant_code');
SET @sql := IF(@has_new_uk = 0,
    'ALTER TABLE `sys_notification_template` ADD UNIQUE KEY `uk_tenant_code` (`tenant_id`, `code`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_tenant_status_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_notification_template' AND INDEX_NAME = 'idx_tenant_status');
SET @sql := IF(@has_tenant_status_idx = 0,
    'ALTER TABLE `sys_notification_template` ADD KEY `idx_tenant_status` (`tenant_id`, `status`)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 插入默认分组（分组仍是平台级全局目录，不随租户隔离）
INSERT IGNORE INTO `sys_notification_template_group` (`id`, `name`, `code`, `description`, `status`, `sort`, `create_by`, `create_time`) VALUES
(1, '系统通知', 'system', '系统级通知模板', 1, 1, 1, NOW()),
(2, '业务通知', 'business', '业务级通知模板', 1, 2, 1, NOW()),
(3, '审批通知', 'approval', '审批相关通知模板', 1, 3, 1, NOW());

-- 插入默认模板（tenant_id=0 为平台默认模板）
INSERT IGNORE INTO `sys_notification_template` (`id`, `tenant_id`, `group_id`, `name`, `code`, `title_template`, `content_template`, `type`, `level`, `status`, `create_by`, `create_time`) VALUES
(1, 0, 1, '系统公告', 'system_announce', '${title}', '${content}', 'system', 'info', 1, 1, NOW()),
(2, 0, 1, '密码到期提醒', 'password_expire', '密码到期提醒', '您的密码将于 ${expireDate} 到期，请及时修改。', 'system', 'warning', 1, 1, NOW()),
(3, 0, 3, '请假审批', 'leave_approval', '${userName} 的请假申请', '${userName} 申请 ${leaveType} ${duration} 天，请审批。', 'biz', 'info', 1, 1, NOW());

-- 存量迁移（幂等）：把平台默认模板（tenant_id=0）克隆到每个启用租户。
-- 不克隆则移除忽略清单后，原有全局模板对任何租户都不可见（等于老数据丢失）。
-- 新增租户时同样需要执行本段（或在租户上下文中导入默认模板），否则新租户看不到默认模板。
INSERT INTO `sys_notification_template`
    (`id`, `tenant_id`, `group_id`, `name`, `code`, `title_template`, `content_template`,
     `type`, `level`, `recipient_type`, `status`, `remark`, `create_by`, `create_time`)
SELECT UUID_SHORT(), t.`id`, s.`group_id`, s.`name`, s.`code`, s.`title_template`, s.`content_template`,
       s.`type`, s.`level`, s.`recipient_type`, s.`status`, s.`remark`, s.`create_by`, s.`create_time`
FROM `sys_tenant` t
JOIN `sys_notification_template` s ON s.`tenant_id` = 0
WHERE t.`status` = 1
  AND NOT EXISTS (
      SELECT 1 FROM `sys_notification_template` x
      WHERE x.`tenant_id` = t.`id` AND x.`code` = s.`code`
  );
