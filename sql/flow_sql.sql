-- =============================================
-- 工作流模块 SQL（jffast-flow）
-- 包含：业务扩展表 wf_category + 菜单初始化数据
--
-- 注意：Flowable 引擎表必须先导入（空库不会自动建全；版本行必须与引擎版本一致，
--   否则启动走升级路径并报 FlowableOptimisticLockingException，见 flowable_schema 内说明）。
-- 执行本文件前，需先执行 flowable_schema/flowable_mysql_all.sql 导入 Flowable 引擎表（ACT_* + FLW_*，8.0.0）
-- 执行顺序：flowable_schema/flowable_mysql_all.sql → system_sql.sql → system_init_data.sql → 本文件
--
-- ⚠️ 菜单 ID 段：**3000-3099**（2026-09-22 定）
--
--   本文件的菜单 ID 连撞两次，都是"口头约定段位、没核对实际占用"造成的，记录在此以免第三次：
--     第 1 次选 1500-1599 → 撞 `system_init_data.sql` 里菜单 1005（字典管理）的按钮 1501-1504
--       （base 的约定是「菜单 10XX → 按钮 1X0N」）；
--     第 2 次改 2000-2099 → 又撞菜单 1011（在线用户）的按钮 2001/2002。
--   两次的后果都是：多行 INSERT 主键冲突 → **整条语句失败**（`INSERT IGNORE` 则会静默丢掉那两条），
--   于是「流程分类 / 流程定义」两个菜单不存在 → 前端动态路由少两条 → 访问 `/flow/flowCategory` 时
--   路由未注册，路由守卫 `next('/')` 兜底又找不到 `/` → **无限重定向 + Maximum call stack size exceeded，
--   页面整片空白**（前端 E2E 实测）。
--
--   分配新段的规矩：**先查 `sys_menu` 的实际占用再选**，别按"看起来没人用"拍脑袋。
--   常见占用（2026-09-22 实测）：1000-1011 / 1101-1106 / 1201-1204 / 1301-1304 / 1401-1404 /
--   1501-1504 / 1601 / 3000-3099 / 9000-9033。
--   新增菜单前用 `SELECT id, parent_id, name FROM sys_menu ORDER BY id` 核对实际占用。
-- =============================================

-- ----------------------------
-- Table structure for wf_category（流程分类）
-- ----------------------------
DROP TABLE IF EXISTS `wf_category`;
CREATE TABLE `wf_category` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `category_code` varchar(50) NOT NULL COMMENT '分类编码（对应 Flowable process category，如 leave/expense）',
    `category_name` varchar(100) NOT NULL COMMENT '分类名称',
    `sort` int DEFAULT 0 COMMENT '排序（asc）',
    `status` int DEFAULT 0 COMMENT '状态：0=启用，1=禁用',
    `remark` varchar(500) DEFAULT NULL COMMENT '备注',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `create_by` bigint DEFAULT NULL COMMENT '创建者ID',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` bigint DEFAULT NULL COMMENT '更新者ID',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_code` (`category_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程分类表';

-- ----------------------------
-- 工作流菜单（id 段：3000-3099，见文件头说明）
-- type: M=目录, C=菜单, F=按钮
-- ----------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(3000, 0, '工作流管理', 15, 'LAYOUT', 'flow', 'M', 1, '', 0, 'precise-monitor', '工作流管理目录'),
(3001, 3000, '流程分类', 1, '/flow/category/index', 'flowCategory', 'C', 1, 'flow:category:view', 0, 'folder', '流程分类管理'),
(3002, 3000, '流程定义', 2, '/flow/definition/index', 'flowDefinition', 'C', 1, 'flow:process:view', 0, 'file-paste', '流程定义管理'),
(3003, 3000, '流程实例', 3, '/flow/instance/index', 'flowInstance', 'C', 1, 'flow:process:view', 0, 'play-circle', '运行中流程实例'),
(3004, 3000, '待办任务', 4, '/flow/task/todo', 'flowTaskTodo', 'C', 1, 'flow:task:view', 0, 'calendar', '我的待办任务'),
(3005, 3000, '已办任务', 5, '/flow/task/done', 'flowTaskDone', 'C', 1, 'flow:task:view', 0, 'check-circle', '我的已办任务'),
(3006, 3000, '流程设计器', 6, '/flow/designer/index', 'flowDesigner', 'C', 1, 'flow:process:deploy', 0, 'edit-2', 'BPMN 在线设计器');

-- 流程分类按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(3011, 3001, '分类查询', 1, '', NULL, 'F', 1, 'flow:category:view', 0, '#', ''),
(3012, 3001, '分类新增', 2, '', NULL, 'F', 1, 'flow:category:add', 0, '#', ''),
(3013, 3001, '分类修改', 3, '', NULL, 'F', 1, 'flow:category:update', 0, '#', ''),
(3014, 3001, '分类删除', 4, '', NULL, 'F', 1, 'flow:category:delete', 0, '#', '');

-- 流程定义按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(3021, 3002, '流程部署', 1, '', NULL, 'F', 1, 'flow:process:deploy', 0, '#', ''),
(3022, 3002, '流程挂起', 2, '', NULL, 'F', 1, 'flow:process:deploy', 0, '#', ''),
(3023, 3002, '流程激活', 3, '', NULL, 'F', 1, 'flow:process:deploy', 0, '#', ''),
(3024, 3002, '流程删除', 4, '', NULL, 'F', 1, 'flow:process:deploy', 0, '#', ''),
-- 查询按钮：与 sys_task.sql 的「任务查询」(2203) 同因——超管的按钮权限集只由 F 行组成
-- （SysMenuService.listAllBtnPerm 过滤 type='F'），只在 C 行上挂码时该码没有可授予的按钮形态。
(3025, 3002, '流程定义查询', 5, '', NULL, 'F', 1, 'flow:process:view', 0, '#', '');

-- 流程实例按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(3031, 3003, '发起流程', 1, '', NULL, 'F', 1, 'flow:process:start', 0, '#', ''),
(3032, 3003, '取消实例', 2, '', NULL, 'F', 1, 'flow:process:cancel', 0, '#', '');

-- 任务按钮
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(3041, 3004, '签收任务', 1, '', NULL, 'F', 1, 'flow:task:claim', 0, '#', ''),
(3042, 3004, '审批任务', 2, '', NULL, 'F', 1, 'flow:task:complete', 0, '#', ''),
(3043, 3004, '转办任务', 3, '', NULL, 'F', 1, 'flow:task:delegate', 0, '#', ''),
(3044, 3004, '任务查询', 4, '', NULL, 'F', 1, 'flow:task:view', 0, '#', '');

-- ----------------------------
-- 默认流程分类数据
-- ----------------------------
INSERT IGNORE INTO `wf_category` (`id`, `category_code`, `category_name`, `sort`, `status`, `remark`) VALUES
(1, 'leave', '请假流程', 1, 0, '员工请假审批'),
(2, 'expense', '报销流程', 2, 0, '费用报销审批'),
(3, 'procurement', '采购流程', 3, 0, '采购申请审批');
