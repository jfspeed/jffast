-- =============================================
-- 文件管理（sys_file）：表结构 + 菜单种子
-- 幂等脚本：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE）
-- 新建库 / 存量库均执行本文件即可
-- 说明：上传由 jffast-starter-file 自动登记（历史文件不回填，仅登记新上传）
--
-- ⚠️ 归属模型（2026-11-09 改造）：
--   * tenant_id 改为 NOT NULL DEFAULT 0（与 sys_role 同口径：0 = 平台/单租户）
--   * url 是**内部存储定位键**（/upload/... 形态），**禁止对外直出**；
--     对外只暴露短期签名 URL（/sys/file/view/{id}?ticket=...）
--   * 业务表（sys_user.avatar / demo_example.image）存稳定引用 file:{id}，
--     签名 URL 有 TTL，绝不落库
--   * 存量库请另跑 sys_file_migration.sql（幂等迁移 + 孤儿清单）
-- =============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 文件记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_file` (
    `id`            bigint       NOT NULL COMMENT '文件ID',
    `tenant_id`     bigint       NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台/单租户；与 sys_role 同口径）',
    `file_name`     varchar(255) NOT NULL COMMENT '原始文件名',
    `url`           varchar(500) NOT NULL COMMENT '内部存储定位键（/upload/... 形态），禁止对外直出',
    `size`          bigint       DEFAULT NULL COMMENT '文件大小（字节）',
    `mime`          varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
    `biz_type`      varchar(64)  DEFAULT NULL COMMENT '业务类型（上传前缀）',
    `storage_type`  varchar(32)  DEFAULT NULL COMMENT '存储类型（local/minio/aliyun-oss/tencent-cos）',
    `create_by`     bigint       DEFAULT NULL COMMENT '创建者ID',
    `create_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     bigint       DEFAULT NULL COMMENT '更新者ID',
    `update_time`   datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_create_time` (`create_time`) USING BTREE,
    KEY `idx_url` (`url`) USING BTREE,
    KEY `idx_tenant_id` (`tenant_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='文件记录表';

-- ----------------------------
-- 存量库对齐（幂等）：把历史 NULL 的 tenant_id 落 0，并改成 NOT NULL DEFAULT 0
-- 写法复用 social_bind.sql 的 information_schema + 动态 SQL 幂等套路
-- ----------------------------
UPDATE `sys_file` SET `tenant_id` = 0 WHERE `tenant_id` IS NULL;

SET @ddl := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` MODIFY COLUMN `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT ''租户ID（0=平台/单租户；与 sys_role 同口径）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_file'
      AND COLUMN_NAME = 'tenant_id'
      AND IS_NULLABLE = 'NO'
      AND COLUMN_DEFAULT = '0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 租户维度索引（显式过滤用；重复执行不报错）
SET @idx := (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD KEY `idx_tenant_id` (`tenant_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_file'
      AND INDEX_NAME = 'idx_tenant_id'
);
PREPARE stmt FROM @idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 文件管理菜单（页面 id=1014，按钮 id=2301-2303，parent=1000 系统管理）
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1014, 1000, '文件管理', 14, '/system/file/index', 'systemFile', 'C', 1, 'system:file:view', 0, 'file', '文件管理菜单');

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2301, 1014, '文件查询', 1, '', NULL, 'F', 1, 'system:file:view', 0, '#', ''),
(2302, 1014, '文件下载', 2, '', NULL, 'F', 1, 'system:file:download', 0, '#', ''),
(2303, 1014, '文件删除', 3, '', NULL, 'F', 1, 'system:file:delete', 0, '#', '');
