-- ============================================================
-- 社交账号绑定表（sys_social_bind）
--
-- tenant_id 说明（2026-10-13 补）：
--   登录态的绑定/解绑/列表走的是**用户会话**，Pro 多租户开启后请求带租户上下文，
--   租户拦截器会给本表 SQL 追加 tenant_id 条件。缺列时：
--     · 个人中心「社交绑定」列表 → Unknown column（前端降级为空）
--     · 绑定回调写库 → 500
--   故本表带 tenant_id（实体不映射，拦截器 SQL 层注入，与 sys_user 等表同口径）。
--   未登录的社交登录查询无租户上下文，不受影响。
--
-- 幂等性：CREATE IF NOT EXISTS 对存量库不生效，故文件尾部对 tenant_id 列做
-- information_schema 判断 + 动态 SQL 补列（列已存在/表不存在都退化为 DO 0），
-- 保证可反复执行。MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`。
-- ============================================================
CREATE TABLE IF NOT EXISTS `sys_social_bind` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户ID',
  `user_id` BIGINT NOT NULL COMMENT '系统用户ID',
  `provider` VARCHAR(32) NOT NULL COMMENT '社交平台标识(github/gitee等)',
  `open_id` VARCHAR(128) NOT NULL COMMENT '社交平台用户ID',
  `username` VARCHAR(128) DEFAULT NULL COMMENT '社交平台用户名',
  `nickname` VARCHAR(128) DEFAULT NULL COMMENT '社交平台昵称',
  `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '社交平台头像URL',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_openid` (`provider`, `open_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社交账号绑定表';

-- sys_social_bind.tenant_id（存量库升级）
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_social_bind') > 0
        AND (SELECT COUNT(*) FROM information_schema.COLUMNS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_social_bind'
            AND COLUMN_NAME = 'tenant_id') = 0,
        'ALTER TABLE `sys_social_bind` ADD COLUMN `tenant_id` BIGINT DEFAULT NULL COMMENT ''租户ID'' AFTER `id`',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- sys_social_bind 索引（存量库升级）
SET @ddl := (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_social_bind') > 0
        AND (SELECT COUNT(*) FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_social_bind'
            AND INDEX_NAME = 'idx_tenant_id') = 0,
        'ALTER TABLE `sys_social_bind` ADD INDEX `idx_tenant_id` (`tenant_id`)',
        'DO 0'
    )
);
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
