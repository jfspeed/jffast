-- =============================================
-- 上传文件归属模型迁移（sys_file 归属 + 业务表稳定引用）
-- 幂等脚本：**可重复执行**，重复执行结果一致
--
-- 背景（2026-11-09 改造）：
--   * 旧模型：上传只落盘，业务表（sys_user.avatar / demo_example.image）存裸路径
--     `/upload/xxx`，而 `/upload/**` 是静态映射直出——登录即可读任意路径。
--   * 新模型：文件读统一走 /sys/file/view/{id}?ticket=...（签名）与
--     /sys/file/download/{id}（受控），业务表改存稳定引用 `file:{id}`。
--
-- 本脚本做四件事：
--   1. sys_file.tenant_id 回填 0 并对齐 NOT NULL DEFAULT 0（与 sys_role 同口径）
--   2. 业务表的裸路径 → `file:{id}` 稳定引用（长度感知匹配：精确 → 前缀）
--   3. 无主文件（孤儿）置 NULL（走默认占位图），**磁盘文件不删**
--   4. 迁移前落快照表，可回滚；三类孤儿分别计数输出
--
-- 执行前请先备份（快照表只存「原始值」，不回滚磁盘文件）
-- =============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 1) sys_file.tenant_id 回填 + DDL 对齐
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

-- ----------------------------
-- 2) 快照表（原始值，只写一次 ⇒ 重复执行不会覆盖成迁移后的值）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_file_migration_snapshot` (
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '快照ID',
    `biz_table`   varchar(64)  NOT NULL COMMENT '业务表名（sys_user / demo_example）',
    `biz_id`      bigint       NOT NULL COMMENT '业务行ID',
    `old_value`   varchar(500) DEFAULT NULL COMMENT '迁移前的原始值',
    `new_value`   varchar(500) DEFAULT NULL COMMENT '迁移后的值（回滚用）',
    `orphan_type` varchar(32)  DEFAULT NULL COMMENT 'ORPHAN_NO_RECORD / ORPHAN_TRUNCATED / ORPHAN_NEW_SHAPE',
    `migrated_at` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '快照时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_biz` (`biz_table`, `biz_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='文件归属迁移快照（回滚用）';

-- 只在「该业务行还没有快照」时写入原始值
INSERT INTO `sys_file_migration_snapshot` (`biz_table`, `biz_id`, `old_value`)
SELECT 'sys_user', u.`id`, u.`avatar`
FROM `sys_user` u
WHERE u.`avatar` IS NOT NULL
  AND u.`avatar` <> ''
  AND NOT EXISTS (
      SELECT 1 FROM `sys_file_migration_snapshot` s
      WHERE s.`biz_table` = 'sys_user' AND s.`biz_id` = u.`id`
  );

-- ----------------------------
-- 3) 分类（三类孤儿）
--    * ORPHAN_NO_RECORD：既无精确匹配也无前缀匹配 ⇒ 无主文件，置 NULL
--    * ORPHAN_TRUNCATED：前缀命中但长度不足（历史 varchar(255) 截断）⇒ 按前缀反查迁移
--    * ORPHAN_NEW_SHAPE：已是 /sys/file/view/ 签名形态 ⇒ 不动（仅计数）
-- ----------------------------
UPDATE `sys_file_migration_snapshot` s
SET s.`orphan_type` = CASE
    WHEN s.`old_value` LIKE '/sys/file/view/%' THEN 'ORPHAN_NEW_SHAPE'
    WHEN EXISTS (SELECT 1 FROM `sys_file` f WHERE f.`url` = s.`old_value`) THEN NULL
    WHEN EXISTS (SELECT 1 FROM `sys_file` f WHERE f.`url` LIKE CONCAT(s.`old_value`, '%')) THEN 'ORPHAN_TRUNCATED'
    ELSE 'ORPHAN_NO_RECORD'
END
WHERE s.`biz_table` = 'sys_user';

-- 迁移：精确命中的行 → file:{id}
UPDATE `sys_user` u
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = 'sys_user' AND s.`biz_id` = u.`id`
JOIN `sys_file` f ON f.`url` = s.`old_value`
SET u.`avatar` = CONCAT('file:', f.`id`),
    s.`new_value` = CONCAT('file:', f.`id`),
    s.`orphan_type` = COALESCE(s.`orphan_type`, 'MIGRATED_EXACT')
WHERE s.`old_value` NOT LIKE '/sys/file/view/%';

-- 迁移：前缀命中（截断）的行 → file:{id}（取 id 最小的一条，结果确定）
UPDATE `sys_user` u
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = 'sys_user' AND s.`biz_id` = u.`id`
SET u.`avatar` = CONCAT('file:', (
        SELECT MIN(f.`id`) FROM `sys_file` f
        WHERE f.`url` LIKE CONCAT(s.`old_value`, '%')
    )),
    s.`new_value` = CONCAT('file:', (
        SELECT MIN(f.`id`) FROM `sys_file` f
        WHERE f.`url` LIKE CONCAT(s.`old_value`, '%')
    )),
    s.`orphan_type` = 'ORPHAN_TRUNCATED'
WHERE s.`orphan_type` = 'ORPHAN_TRUNCATED';

-- 无主文件：置 NULL（头像走默认占位图；磁盘文件不删）
UPDATE `sys_user` u
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = 'sys_user' AND s.`biz_id` = u.`id`
SET u.`avatar` = NULL,
    s.`new_value` = NULL
WHERE s.`orphan_type` = 'ORPHAN_NO_RECORD';

-- ----------------------------
-- 4) demo_example（表可能不存在，用动态 SQL 幂等处理）
-- ----------------------------
SET @has_demo := (SELECT COUNT(*) FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demo_example');

SET @demo_snapshot := IF(@has_demo > 0, '
INSERT INTO `sys_file_migration_snapshot` (`biz_table`, `biz_id`, `old_value`)
SELECT ''demo_example'', d.`id`, d.`image`
FROM `demo_example` d
WHERE d.`image` IS NOT NULL
  AND d.`image` <> ''''
  AND NOT EXISTS (
      SELECT 1 FROM `sys_file_migration_snapshot` s
      WHERE s.`biz_table` = ''demo_example'' AND s.`biz_id` = d.`id`
  );', 'DO 0');
PREPARE stmt FROM @demo_snapshot;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @demo_classify := IF(@has_demo > 0, '
UPDATE `sys_file_migration_snapshot` s
SET s.`orphan_type` = CASE
    WHEN s.`old_value` LIKE ''/sys/file/view/%'' THEN ''ORPHAN_NEW_SHAPE''
    WHEN EXISTS (SELECT 1 FROM `sys_file` f WHERE f.`url` = s.`old_value`) THEN NULL
    WHEN EXISTS (SELECT 1 FROM `sys_file` f WHERE f.`url` LIKE CONCAT(s.`old_value'', ''%'')) THEN ''ORPHAN_TRUNCATED''
    ELSE ''ORPHAN_NO_RECORD''
END
WHERE s.`biz_table` = ''demo_example'';', 'DO 0');
PREPARE stmt FROM @demo_classify;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @demo_exact := IF(@has_demo > 0, '
UPDATE `demo_example` d
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = ''demo_example'' AND s.`biz_id` = d.`id`
JOIN `sys_file` f ON f.`url` = s.`old_value`
SET d.`image` = CONCAT(''file:'', f.`id`),
    s.`new_value` = CONCAT(''file:'', f.`id`),
    s.`orphan_type` = COALESCE(s.`orphan_type`, ''MIGRATED_EXACT'')
WHERE s.`old_value` NOT LIKE ''/sys/file/view/%'';', 'DO 0');
PREPARE stmt FROM @demo_exact;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @demo_trunc := IF(@has_demo > 0, '
UPDATE `demo_example` d
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = ''demo_example'' AND s.`biz_id` = d.`id`
SET d.`image` = CONCAT(''file:'', (
        SELECT MIN(f.`id`) FROM `sys_file` f
        WHERE f.`url` LIKE CONCAT(s.`old_value`, ''%'')
    )),
    s.`new_value` = CONCAT(''file:'', (
        SELECT MIN(f.`id`) FROM `sys_file` f
        WHERE f.`url` LIKE CONCAT(s.`old_value`, ''%'')
    )),
    s.`orphan_type` = ''ORPHAN_TRUNCATED''
WHERE s.`orphan_type` = ''ORPHAN_TRUNCATED'';', 'DO 0');
PREPARE stmt FROM @demo_trunc;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @demo_orphan := IF(@has_demo > 0, '
UPDATE `demo_example` d
JOIN `sys_file_migration_snapshot` s ON s.`biz_table` = ''demo_example'' AND s.`biz_id` = d.`id`
SET d.`image` = NULL,
    s.`new_value` = NULL
WHERE s.`orphan_type` = ''ORPHAN_NO_RECORD'';', 'DO 0');
PREPARE stmt FROM @demo_orphan;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 5) 迁移报告：三类孤儿 + 已迁移行数（重复执行结果一致）
-- ----------------------------
SELECT s.`biz_table`,
       COALESCE(s.`orphan_type`, 'MIGRATED_EXACT') AS `result`,
       COUNT(*) AS `rows`
FROM `sys_file_migration_snapshot` s
GROUP BY s.`biz_table`, COALESCE(s.`orphan_type`, 'MIGRATED_EXACT')
ORDER BY s.`biz_table`, `result`;

-- 回滚参考（不自动执行，人工确认后跑）：
-- UPDATE `sys_user` u JOIN `sys_file_migration_snapshot` s
--   ON s.`biz_table` = 'sys_user' AND s.`biz_id` = u.`id`
-- SET u.`avatar` = s.`old_value` WHERE s.`new_value` IS NOT NULL;
-- UPDATE `demo_example` d JOIN `sys_file_migration_snapshot` s
--   ON s.`biz_table` = 'demo_example' AND s.`biz_id` = d.`id`
-- SET d.`image` = s.`old_value` WHERE s.`new_value` IS NOT NULL;
