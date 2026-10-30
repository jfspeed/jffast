-- =============================================
-- 岗位管理（sys_post）：表结构 + 菜单种子
-- 幂等脚本：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE）
-- 新建库 / 存量库均执行本文件即可
-- =============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 岗位表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_post` (
    `id`          bigint       NOT NULL COMMENT '岗位ID',
    `tenant_id`   bigint       DEFAULT NULL COMMENT '租户ID',
    `post_code`   varchar(64)  NOT NULL COMMENT '岗位编码',
    `post_name`   varchar(64)  NOT NULL COMMENT '岗位名称',
    `sort`        int          NOT NULL DEFAULT 0 COMMENT '排序',
    `state`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态（1正常 0停用）',
    `remark`      varchar(255) DEFAULT NULL COMMENT '备注',
    `create_by`   bigint       DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`   bigint       DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime     DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_post_code` (`post_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='岗位表';

-- ----------------------------
-- 用户岗位关联表（一人多岗；与 sys_user_role / sys_user_dept 同构）
-- 幂等：可重复执行。存量库重跑本文件即可拿到这张表。
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_user_post` (
    `user_id`   bigint NOT NULL COMMENT '用户ID',
    `post_id`   bigint NOT NULL COMMENT '岗位ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    PRIMARY KEY (`post_id`, `user_id`) USING BTREE,
    KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='用户岗位关联';

-- ----------------------------
-- 岗位管理菜单（页面 id=1012，按钮 id=2101-2104，parent=1000 系统管理）
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1012, 1000, '岗位管理', 12, '/system/post/index', 'systemPost', 'C', 1, 'system:post:view', 0, 'user-list', '岗位管理菜单');

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2101, 1012, '岗位查询', 1, '', NULL, 'F', 1, 'system:post:view', 0, '#', ''),
(2102, 1012, '岗位新增', 2, '', NULL, 'F', 1, 'system:post:add', 0, '#', ''),
(2103, 1012, '岗位修改', 3, '', NULL, 'F', 1, 'system:post:update', 0, '#', ''),
(2104, 1012, '岗位删除', 4, '', NULL, 'F', 1, 'system:post:delete', 0, '#', '');
