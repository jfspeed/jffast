-- =============================================
-- 文件管理（sys_file）：表结构 + 菜单种子
-- 幂等脚本：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE）
-- 新建库 / 存量库均执行本文件即可
-- 说明：上传由 jffast-starter-file 自动登记（历史文件不回填，仅登记新上传）
-- =============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 文件记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_file` (
    `id`            bigint       NOT NULL COMMENT '文件ID',
    `tenant_id`     bigint       DEFAULT NULL COMMENT '租户ID',
    `file_name`     varchar(255) NOT NULL COMMENT '原始文件名',
    `url`           varchar(500) NOT NULL COMMENT '访问路径/URL',
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
    KEY `idx_url` (`url`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='文件记录表';

-- ----------------------------
-- 文件管理菜单（页面 id=1014，按钮 id=2301-2303，parent=1000 系统管理）
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1014, 1000, '文件管理', 14, '/system/file/index', 'systemFile', 'C', 1, 'system:file:view', 0, 'file', '文件管理菜单');

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2301, 1014, '文件查询', 1, '', NULL, 'F', 1, 'system:file:view', 0, '#', ''),
(2302, 1014, '文件下载', 2, '', NULL, 'F', 1, 'system:file:download', 0, '#', ''),
(2303, 1014, '文件删除', 3, '', NULL, 'F', 1, 'system:file:delete', 0, '#', '');
