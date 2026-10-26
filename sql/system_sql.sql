-- =============================================
-- sys_ 开头的表（系统表）
-- =============================================
-- ⚠️ 危险脚本：本文件包含 DROP TABLE IF EXISTS，会清空同名表数据。
--    仅用于全新库初始化或明确要重置系统表的场景；生产/存量库执行前必须先备份。
-- =============================================

-- ----------------------------
-- Table structure for sys_tenant
-- ----------------------------
DROP TABLE IF EXISTS `sys_tenant`;
CREATE TABLE `sys_tenant` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_name` varchar(100) NOT NULL COMMENT '租户名称',
    `tenant_code` varchar(50) NOT NULL COMMENT '租户编码(唯一标识)',
    `contact_name` varchar(50) DEFAULT NULL COMMENT '联系人',
    `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
    `contact_email` varchar(100) DEFAULT NULL COMMENT '联系邮箱',
    `status` int NOT NULL DEFAULT 1 COMMENT '租户状态：0-禁用 1-启用',
    `expire_time` varchar(30) DEFAULT NULL COMMENT '到期时间',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`tenant_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户管理表';

DROP TABLE IF EXISTS `sys_log`;
-- sys_log definition

CREATE TABLE `sys_log` (
                           `id` bigint NOT NULL,
                           `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                           `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                           `ip` varchar(64) DEFAULT NULL COMMENT '用户ip',
                           `title` varchar(20) DEFAULT NULL COMMENT '标题，操作方法描述',
                           `method` varchar(50) DEFAULT NULL COMMENT '操作方法',
                           `info` varchar(5000) DEFAULT NULL COMMENT '详情',
                           `state` varchar(10) DEFAULT NULL COMMENT '状态',
                           -- level / request_uri / duration 三列曾只存在于单独的增量脚本里，而交付
                           -- runbook 的前置链**没有列它**，于是客户按文档部署后，SysLog 实体（映射
                           -- 这三列）一写审计日志就 `Unknown column 'level'` → 所有带 @SLog 的增删改
                           -- 全部 500（2026-09-22 空库复核暴露）。项目尚未发布、没有需要升级的存量库，
                           -- 故三列直接内建在建表语句里，不再保留单独的增量脚本。
                           `level` varchar(20) DEFAULT NULL COMMENT '日志级别：info/warning/error',
                           `request_uri` varchar(500) DEFAULT NULL COMMENT '请求URI',
                           `duration` bigint DEFAULT NULL COMMENT '执行时长（毫秒）',
                           PRIMARY KEY (`id`) USING BTREE,
                           KEY `idx_level` (`level`),
                           KEY `idx_request_uri` (`request_uri`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;



-- ----------------------------
-- Table structure for sys_dict
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict`;
CREATE TABLE `sys_dict` (
                            `id` bigint NOT NULL COMMENT 'id',
                            `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                            `parent_id` bigint DEFAULT NULL COMMENT '父级id',
                            `name` varchar(50) DEFAULT NULL COMMENT '字典名称',
                            `code` varchar(50) DEFAULT NULL COMMENT '字典代码',
                            `value` varchar(50) DEFAULT NULL COMMENT '字典值',
                            `is_type` tinyint(1) DEFAULT NULL COMMENT '是否是字典类型（1 字典类型 2 字典项）',
                            `sort` int(11) DEFAULT NULL COMMENT '排序',
                            `remark` varchar(255) DEFAULT NULL COMMENT '备注',
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='字典';

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
                            `id` bigint NOT NULL COMMENT '部门id',
                            `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                            `parent_id` bigint DEFAULT NULL COMMENT '父部门id',
                            `dept_name` varchar(30) DEFAULT NULL COMMENT '部门名称',
                            `level` tinyint(1) DEFAULT NULL COMMENT '层级（1 根目录 2 单位 3 部门）',
                            `remark` varchar(255) DEFAULT NULL COMMENT '备注',
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='部门';

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
                            `id` bigint NOT NULL COMMENT '菜单ID',
                            `parent_id` bigint DEFAULT NULL COMMENT '父菜单ID',
                            `name` varchar(50) NOT NULL COMMENT '菜单名称',
                            `sort` int(11) DEFAULT '0' COMMENT '显示顺序',
                            `url` varchar(200) DEFAULT '' COMMENT '访问路径',
                            `path` varchar(100) DEFAULT NULL COMMENT '组件名称',
                            `type` char(1) DEFAULT NULL COMMENT '菜单类型（M目录 C菜单 F按钮）',
                            `state` tinyint(1) DEFAULT '1' COMMENT '菜单状态（1正常 2停用 3删除）',
                            `perms` varchar(100) DEFAULT NULL COMMENT '权限标识',
                            `visible` tinyint(1) DEFAULT '0' COMMENT '显示状态（0 显示 1隐藏）',
                            `icon` varchar(100) DEFAULT '#' COMMENT '菜单图标',
                            `active_menu` varchar(50) DEFAULT NULL,
                            `remark` varchar(500) DEFAULT '' COMMENT '备注',
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            UNIQUE KEY `uk_path` (`path`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='菜单权限表';

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
                            `id` bigint NOT NULL COMMENT '角色ID',
                            `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户ID（0=单租户/未启用租户隔离；启用商业版多租户后写入真实租户ID）',
                            `role_key` varchar(50) NOT NULL COMMENT '角色权限字符',
                            `role_name` varchar(50) NOT NULL COMMENT '角色名称',
                            `is_open` tinyint(1) DEFAULT '1' COMMENT '菜单树是否展开（0折叠 1展开 ）',
                            `data_scope` tinyint(1) DEFAULT '1' COMMENT '数据范围（1 所有数据 2 所在部门及子部门数据 3 所在部门数据 4 仅本人数据 5 自定义数据）',
                            `remark` varchar(255) DEFAULT NULL COMMENT '备注',
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            -- role_key 是授权判定依据（selectOne(eq("role_key", ...))），重名会让角色创建链直接报错。
                            -- 应用层查重是「先查后插」，并发下两个请求可同时通过，故唯一性由数据库兜底。
                            -- 唯一维度必须带 tenant_id：不带则商业版多租户下两个租户无法各自拥有 common/super_admin。
                            -- tenant_id 因此必须 NOT NULL DEFAULT 0 —— 可空列在 MySQL 唯一索引里不受约束（NULL 行想几条有几条）。
                            -- 已有库升级：先 SELECT tenant_id, role_key, COUNT(*) ... HAVING COUNT(*)>1 清干净，再 ALTER，否则加索引直接失败。
                            UNIQUE KEY `uk_sys_role_tenant_role_key` (`tenant_id`, `role_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='角色信息表';

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept` (
                                 `role_id` bigint NOT NULL COMMENT '角色ID',
                                 `dept_id` bigint NOT NULL COMMENT '部门ID',
                                 `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                 PRIMARY KEY (`role_id`,`dept_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='角色和部门关联表（当角色数据范围为自定义数据时使用）';

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
                                 `role_id` bigint NOT NULL COMMENT '角色ID',
                                 `menu_id` bigint NOT NULL COMMENT '菜单ID',
                                 PRIMARY KEY (`role_id`,`menu_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='角色菜单关联';

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
-- sys_user definition
CREATE TABLE `sys_user` (
                            `id` bigint NOT NULL COMMENT '用户编号',
                            `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                            `user_name` varchar(20) NOT NULL COMMENT '用户名称',
                            `real_name` varchar(20) NOT NULL COMMENT '用户姓名',
                            `nick_name` varchar(50) DEFAULT NULL COMMENT '昵称',
                            `dept_id` bigint DEFAULT NULL COMMENT '所属部门',
                            `password` varchar(100) DEFAULT NULL COMMENT '密码',
                            `salt` varchar(50) DEFAULT NULL COMMENT '盐值',
                            `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '头像',
                            `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
                            `email` varchar(50) DEFAULT NULL COMMENT 'email',
                            `state` tinyint(1) DEFAULT '1' COMMENT '状态（1正常 2停用）',
                            `role_id` varchar(500) DEFAULT NULL COMMENT '角色id',
                            `login_date` datetime DEFAULT NULL COMMENT '最后登录时间',
                            `update_pwd_time` datetime DEFAULT NULL COMMENT '密码更改时间',
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`) USING BTREE,
                            KEY `dept_id` (`dept_id`) USING BTREE,
                            -- 唯一性必须由数据库兜底：应用层的「先查后插」存在竞态窗口，
                            -- 两个管理员并发建同名用户会同时通过校验。用户名重复会让该账号
                            -- 的登录彻底不可用（mapper 按 user_name 查会命中多行），
                            -- 所以这里给 user_name / phone 加唯一约束。
                            -- phone 可空，MySQL 唯一索引允许多个 NULL，不影响未填手机号的用户。
                            UNIQUE KEY `uk_user_name` (`user_name`) USING BTREE,
                            UNIQUE KEY `uk_phone` (`phone`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='用户信息';

-- ----------------------------
-- Table structure for sys_user_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_dept`;
CREATE TABLE `sys_user_dept` (
                                 `user_id` bigint NOT NULL COMMENT '用户ID',
                                 `dept_id` bigint NOT NULL COMMENT '部门ID',
                                 `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                 PRIMARY KEY (`dept_id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='用户部门关联';

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
                                 `user_id` bigint NOT NULL COMMENT '用户ID',
                                 `role_id` bigint NOT NULL COMMENT '角色ID',
                                 `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                                 PRIMARY KEY (`role_id`,`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='用户角色关联';

-- ----------------------------
-- Table structure for sys_safe
-- ----------------------------
DROP TABLE IF EXISTS `sys_safe`;
CREATE TABLE `sys_safe` (
                            `id` bigint NOT NULL,
                            `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
                            `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
                            `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
                            `pwd_cycle` int(11) DEFAULT NULL COMMENT '密码更改周期（90天，60天，30天，0无）',
                            `pwd_login_limit` tinyint(1) DEFAULT NULL COMMENT '密码登录限制（0：连续错3次，锁定账号15分钟。1：连续错5次，锁定账号30分钟）',
                            `idle_time_setting` tinyint(1) DEFAULT NULL COMMENT '闲置时间设置（0：无。1：空闲30分钟，系统默认用户退出）',
                            PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `job_name` varchar(64) NOT NULL COMMENT '任务名称',
    `job_group` varchar(64) DEFAULT 'DEFAULT' COMMENT '任务组名',
    `invoke_target` varchar(500) NOT NULL COMMENT '调用目标（Bean名称.方法名）',
    `cron_expression` varchar(255) NOT NULL COMMENT 'CRON表达式',
    `misfire_policy` varchar(20) DEFAULT '3' COMMENT '计划执行策略（1立即执行 2放弃执行 3下次执行）',
    `concurrent` tinyint(1) DEFAULT '0' COMMENT '是否并发执行（0否 1是）',
    `status` tinyint(1) DEFAULT '1' COMMENT '状态（0暂停 1正常）',
    `service_name` varchar(64) DEFAULT NULL COMMENT '执行该任务的服务名（spring.application.name）；NULL=由默认调度者执行',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_name_group` (`job_name`, `job_group`),
    KEY `idx_service_name` (`service_name`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务表';

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `job_name` varchar(64) NOT NULL COMMENT '任务名称',
    `job_group` varchar(64) DEFAULT 'DEFAULT' COMMENT '任务组名',
    `invoke_target` varchar(500) DEFAULT NULL COMMENT '调用目标',
    `status` tinyint(1) DEFAULT '0' COMMENT '执行状态（1成功 2失败）',
    `error_msg` varchar(2000) DEFAULT NULL COMMENT '错误信息',
    `start_time` datetime DEFAULT NULL COMMENT '开始时间',
    `end_time` datetime DEFAULT NULL COMMENT '结束时间',
    `duration` bigint DEFAULT NULL COMMENT '耗时（毫秒）',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_job_name` (`job_name`),
    KEY `idx_job_group` (`job_group`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志表';

-- ----------------------------
-- Table structure for sys_notification
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification`;
CREATE TABLE `sys_notification` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `title` varchar(200) NOT NULL COMMENT '通知标题',
    `content` text COMMENT '通知内容',
    `type` varchar(50) DEFAULT 'system' COMMENT '通知类型(system-系统通知,biz-业务通知,flow-流程通知)',
    `level` varchar(20) DEFAULT 'info' COMMENT '通知级别(info/warning/error)',
    `sender` varchar(50) DEFAULT NULL COMMENT '发送人',
    `recipient_type` varchar(20) DEFAULT 'all' COMMENT '接收人类型(all-全体,role-角色,dept-部门,user-用户)',
    `recipient_ids` text COMMENT '接收人ID列表(逗号分隔)',
    `status` tinyint(1) DEFAULT '0' COMMENT '状态(0-未发送,1-已发送)',
    `send_time` datetime DEFAULT NULL COMMENT '发送时间',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_status_create` (`status`, `create_time`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统通知表';

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
CREATE TABLE `sys_config` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `config_name` varchar(50) NOT NULL COMMENT '参数名称',
    `config_key` varchar(50) NOT NULL COMMENT '参数键名',
    `config_value` text COMMENT '参数键值',
    `system_builtin` tinyint(1) DEFAULT '0' COMMENT '系统内置(0-否,1-是,内置不可删除)',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数配置表';

-- ----------------------------
-- Table structure for sys_oauth2_client_config
-- 本服务作为 OAuth2 客户端去调用第三方的接入注册配置（与授权服务器侧 sys_oauth2_client 方向相反）
-- ----------------------------
DROP TABLE IF EXISTS `sys_oauth2_client_config`;
CREATE TABLE `sys_oauth2_client_config` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `provider` varchar(64) NOT NULL COMMENT '第三方唯一标识，如 github/generic',
    `name` varchar(100) DEFAULT NULL COMMENT '接入名称',
    `client_id` varchar(200) DEFAULT NULL COMMENT '客户端ID',
    `client_secret` varchar(500) DEFAULT NULL COMMENT '客户端密钥(仅写入，序列化不输出)',
    `grant_types` varchar(100) DEFAULT NULL COMMENT '授权类型，逗号分隔：client_credentials,authorization_code',
    `authorization_url` varchar(500) DEFAULT NULL COMMENT '授权地址 authorization_code 用',
    `token_url` varchar(500) DEFAULT NULL COMMENT '令牌地址',
    `redirect_uri` varchar(500) DEFAULT NULL COMMENT '回调地址 authorization_code 用',
    `auth_method` varchar(50) DEFAULT NULL COMMENT '认证方式：client_secret_post / client_secret_basic',
    `scopes` varchar(255) DEFAULT NULL COMMENT '授权范围，空格或逗号分隔',
    `ttl` bigint DEFAULT NULL COMMENT '令牌默认有效期(秒)，空则按响应 expires_in',
    `extra` text COMMENT '扩展 JSON',
    `status` tinyint(1) DEFAULT '0' COMMENT '状态：0 禁用 1 启用',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider` (`provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OAuth2客户端接入配置表';

-- ----------------------------
-- Table structure for sys_notification_record
-- ----------------------------
DROP TABLE IF EXISTS `sys_notification_record`;
CREATE TABLE `sys_notification_record` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `notification_id` bigint NOT NULL COMMENT '通知ID',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `is_read` tinyint(1) DEFAULT '0' COMMENT '是否已读(0-未读,1-已读)',
    `read_time` datetime DEFAULT NULL COMMENT '阅读时间',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_notification_id` (`notification_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_user_read` (`user_id`, `is_read`),
    KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知记录表';

-- ============================================================================
-- Quartz 定时任务表（QRTZ_*，JDBC 作业存储）
--
-- DDL 与 sql/quartz.sql **逐字一致**，本脚本自带一份：跑完 system_sql.sql 即可启动，
-- 不必再单独执行 quartz.sql（两份幂等，同跑也不冲突）。
--
-- 为什么必须大写 QRTZ_*：application.yml 里 org.quartz.jobStore.tablePrefix=QRTZ_，
-- Quartz 生成的 SQL 全是大写表名；在 lower_case_table_names=0 的库（Linux、Docker MySQL、
-- 本机 3307 实测=0）上，小写 qrtz_* 会被当成"表不存在"，启动即抛
--     LockException: Failure obtaining db row lock: Table 'QRTZ_LOCKS' doesn't exist
-- 本脚本历史版本曾用小写建表，下面先 DROP 小写遗留表再建大写表，避免一个库里并存两套。
--
-- ⚠️ DROP + CREATE：会清掉调度状态（触发器 / 执行记录），只在初始化或升级 Quartz 时执行。
-- ============================================================================

-- 清理历史版本遗留的小写 qrtz_* 表（lower_case_table_names=0 下与上面的大写表是两套）
DROP TABLE IF EXISTS `qrtz_blob_triggers`;
DROP TABLE IF EXISTS `qrtz_calendars`;
DROP TABLE IF EXISTS `qrtz_cron_triggers`;
DROP TABLE IF EXISTS `qrtz_fired_triggers`;
DROP TABLE IF EXISTS `qrtz_job_details`;
DROP TABLE IF EXISTS `qrtz_locks`;
DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
DROP TABLE IF EXISTS `qrtz_scheduler_state`;
DROP TABLE IF EXISTS `qrtz_simple_triggers`;
DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
DROP TABLE IF EXISTS `qrtz_triggers`;

-- ----------------------------------------------------------------------------
-- 以下逐字取自 quartz-2.3.2.jar!/org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql
-- ----------------------------------------------------------------------------
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

CREATE TABLE QRTZ_JOB_DETAILS(
SCHED_NAME VARCHAR(120) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
JOB_CLASS_NAME VARCHAR(250) NOT NULL,
IS_DURABLE VARCHAR(1) NOT NULL,
IS_NONCONCURRENT VARCHAR(1) NOT NULL,
IS_UPDATE_DATA VARCHAR(1) NOT NULL,
REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
NEXT_FIRE_TIME BIGINT(13) NULL,
PREV_FIRE_TIME BIGINT(13) NULL,
PRIORITY INTEGER NULL,
TRIGGER_STATE VARCHAR(16) NOT NULL,
TRIGGER_TYPE VARCHAR(8) NOT NULL,
START_TIME BIGINT(13) NOT NULL,
END_TIME BIGINT(13) NULL,
CALENDAR_NAME VARCHAR(190) NULL,
MISFIRE_INSTR SMALLINT(2) NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
REPEAT_COUNT BIGINT(7) NOT NULL,
REPEAT_INTERVAL BIGINT(12) NOT NULL,
TIMES_TRIGGERED BIGINT(10) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CRON_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
CRON_EXPRESSION VARCHAR(120) NOT NULL,
TIME_ZONE_ID VARCHAR(80),
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPROP_TRIGGERS
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    STR_PROP_1 VARCHAR(512) NULL,
    STR_PROP_2 VARCHAR(512) NULL,
    STR_PROP_3 VARCHAR(512) NULL,
    INT_PROP_1 INT NULL,
    INT_PROP_2 INT NULL,
    LONG_PROP_1 BIGINT NULL,
    LONG_PROP_2 BIGINT NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 VARCHAR(1) NULL,
    BOOL_PROP_2 VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
    REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_BLOB_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
BLOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
INDEX (SCHED_NAME,TRIGGER_NAME, TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CALENDARS (
SCHED_NAME VARCHAR(120) NOT NULL,
CALENDAR_NAME VARCHAR(190) NOT NULL,
CALENDAR BLOB NOT NULL,
PRIMARY KEY (SCHED_NAME,CALENDAR_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_FIRED_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
ENTRY_ID VARCHAR(95) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
FIRED_TIME BIGINT(13) NOT NULL,
SCHED_TIME BIGINT(13) NOT NULL,
PRIORITY INTEGER NOT NULL,
STATE VARCHAR(16) NOT NULL,
JOB_NAME VARCHAR(190) NULL,
JOB_GROUP VARCHAR(190) NULL,
IS_NONCONCURRENT VARCHAR(1) NULL,
REQUESTS_RECOVERY VARCHAR(1) NULL,
PRIMARY KEY (SCHED_NAME,ENTRY_ID))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SCHEDULER_STATE (
SCHED_NAME VARCHAR(120) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
CHECKIN_INTERVAL BIGINT(13) NOT NULL,
PRIMARY KEY (SCHED_NAME,INSTANCE_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_LOCKS (
SCHED_NAME VARCHAR(120) NOT NULL,
LOCK_NAME VARCHAR(40) NOT NULL,
PRIMARY KEY (SCHED_NAME,LOCK_NAME))
ENGINE=InnoDB;

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME,JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME,CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
