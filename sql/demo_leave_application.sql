-- 请假申请表
CREATE TABLE IF NOT EXISTS `demo_leave_application` (
    `id`              BIGINT          NOT NULL COMMENT '主键ID',
    `user_id`         BIGINT          NOT NULL COMMENT '申请人ID',
    `user_name`       VARCHAR(100)    NOT NULL DEFAULT '' COMMENT '申请人姓名',
    `dept_id`         BIGINT          NULL     COMMENT '申请部门ID',
    `leave_type`      VARCHAR(50)     NOT NULL COMMENT '请假类型：annual-年假/sick-事假/personal-病假/maternity-产假/other-其他',
    `start_time`      DATETIME        NOT NULL COMMENT '开始时间',
    `end_time`        DATETIME        NOT NULL COMMENT '结束时间',
    `duration`        DECIMAL(5,1)    NOT NULL COMMENT '请假时长（天）',
    `reason`          VARCHAR(500)    NULL     COMMENT '请假原因',
    `status`          TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待审批/1-已通过/2-已拒绝/3-已撤回',
    `approver_id`     BIGINT          NULL     COMMENT '审批人ID',
    `approver_name`   VARCHAR(100)    NULL     COMMENT '审批人姓名',
    `approve_time`    DATETIME        NULL     COMMENT '审批时间',
    `approve_remark`  VARCHAR(500)    NULL     COMMENT '审批备注',
    -- 本表**不设** is_deleted：Base 未启用逻辑删除（无 @TableLogic，删除为物理删除），
    -- 建一个没人写、没人过滤的死列只会让"表结构 ↔ 实体"对不上账。
    -- 需要逻辑删除的表：自己加 is_deleted 列 + @TableLogic，两者必须同时到位。
    `create_by`       BIGINT          NULL     COMMENT '创建人ID',
    `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       BIGINT          NULL     COMMENT '更新人ID',
    `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_dept_id` (`dept_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='请假申请表';

-- 默认示例记录：登录后请假申请页直接可见（申请人 admin，待审批状态）
INSERT IGNORE INTO `demo_leave_application` (`id`, `user_id`, `user_name`, `dept_id`, `leave_type`, `start_time`, `end_time`, `duration`, `reason`, `status`, `create_by`, `create_time`, `update_by`, `update_time`) VALUES
(1, 1, 'admin', NULL, 'annual', '2026-10-10 09:00:00', '2026-10-11 18:00:00', 2.0, '演示数据：体验请假申请的增删改查与审批流', 0, 1, NOW(), 1, NOW());
