-- =============================================
-- 异步任务（sys_task）：表结构 + 菜单种子
-- 幂等脚本：可重复执行（CREATE TABLE IF NOT EXISTS / INSERT IGNORE）
-- 新建库 / 存量库均执行本文件即可
-- =============================================

SET NAMES utf8mb4;

-- ----------------------------
-- 异步任务表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `sys_task` (
    `id`            bigint        NOT NULL COMMENT '任务ID',
    `tenant_id`     bigint        DEFAULT NULL COMMENT '租户ID',
    `task_type`     varchar(32)   NOT NULL COMMENT '任务类型（export导出 / import导入）',
    `biz_type`      varchar(32)   NOT NULL COMMENT '业务类型（user用户）',
    `title`         varchar(200)  DEFAULT NULL COMMENT '任务标题',
    `status`        tinyint       NOT NULL DEFAULT 0 COMMENT '状态（0待处理 1进行中 2已完成 3失败）',
    `progress`      int           NOT NULL DEFAULT 0 COMMENT '进度（0-100）',
    `total_rows`    int           DEFAULT NULL COMMENT '总行数',
    `success_rows`  int           DEFAULT NULL COMMENT '成功行数',
    `fail_rows`     int           DEFAULT NULL COMMENT '失败行数',
    `fail_detail`   mediumtext    COMMENT '失败明细（多行文本）',
    `file_path`     varchar(500)  DEFAULT NULL COMMENT '结果文件本地路径',
    `file_name`     varchar(200)  DEFAULT NULL COMMENT '下载文件名',
    `error_msg`     varchar(2000) DEFAULT NULL COMMENT '错误信息',
    `create_by`     bigint        DEFAULT NULL COMMENT '创建者ID',
    `create_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     bigint        DEFAULT NULL COMMENT '更新者ID',
    `update_time`   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_tenant_id` (`tenant_id`) USING BTREE,
    KEY `idx_create_by` (`create_by`) USING BTREE,
    KEY `idx_status` (`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='异步任务表';

-- ----------------------------
-- 异步任务菜单（页面 id=1013，按钮 id=2201-2203，parent=1000 系统管理）
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1013, 1000, '异步任务', 13, '/system/task/index', 'systemTask', 'C', 1, 'system:task:view', 0, 'layers', '异步任务菜单');

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2201, 1013, '任务下载', 1, '', NULL, 'F', 1, 'system:task:download', 0, '#', ''),
(2202, 1013, '任务删除', 2, '', NULL, 'F', 1, 'system:task:delete', 0, '#', ''),
-- 任务查询：路由守卫 meta.perm=system:task:view 依赖该 F 行（超管的 permissions 只由 F 行组成，缺则菜单打回 404）
(2203, 1013, '任务查询', 3, '', NULL, 'F', 1, 'system:task:view', 0, '#', '');
