-- 通知模板分组表
CREATE TABLE IF NOT EXISTS `sys_notification_template_group` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `name`            VARCHAR(100)    NOT NULL COMMENT '分组名称',
    `code`            VARCHAR(100)    NOT NULL COMMENT '分组编码',
    `description`     VARCHAR(500)    NULL     COMMENT '描述',
    `status`          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `sort`            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    `is_deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知模板分组表';

-- 通知模板表
CREATE TABLE IF NOT EXISTS `sys_notification_template` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
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
    `is_deleted`      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_group_id` (`group_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知模板表';

-- 插入默认分组
INSERT IGNORE INTO `sys_notification_template_group` (`id`, `name`, `code`, `description`, `status`, `sort`, `create_by`, `create_time`) VALUES
(1, '系统通知', 'system', '系统级通知模板', 1, 1, 1, NOW()),
(2, '业务通知', 'business', '业务级通知模板', 1, 2, 1, NOW()),
(3, '审批通知', 'approval', '审批相关通知模板', 1, 3, 1, NOW());

-- 插入默认模板
INSERT IGNORE INTO `sys_notification_template` (`id`, `group_id`, `name`, `code`, `title_template`, `content_template`, `type`, `level`, `status`, `create_by`, `create_time`) VALUES
(1, 1, '系统公告', 'system_announce', '${title}', '${content}', 'system', 'info', 1, 1, NOW()),
(2, 1, '密码到期提醒', 'password_expire', '密码到期提醒', '您的密码将于 ${expireDate} 到期，请及时修改。', 'system', 'warning', 1, 1, NOW()),
(3, 3, '请假审批', 'leave_approval', '${userName} 的请假申请', '${userName} 申请 ${leaveType} ${duration} 天，请审批。', 'biz', 'info', 1, 1, NOW());
