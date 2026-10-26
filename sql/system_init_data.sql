-- =============================================
-- 系统模块初始数据（菜单、角色、用户、权限关联）
-- =============================================

-- 确保导入时客户端以 UTF-8 发送中文，避免 MySQL 5.7（默认 latin1）环境下中文乱码
SET NAMES utf8mb4;

-- ----------------------------
-- 首页菜单
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1, 0, '首页', 0, 'LAYOUT', 'dashboard', 'M', 1, '', 0, 'dashboard', '首页目录'),
(2, 1, '概览', 1, '/dashboard/base/index', 'dashboardBase', 'C', 1, '', 0, 'view-list', '首页概览');

-- ----------------------------
-- 示例模块菜单（已迁出 → sql/demo_init_data.sql）
-- ----------------------------
-- 菜单 200-202 与按钮 211-215 属于 **jffast-demo 模块**（对应前端页面 jffast-ui/src/pages/demo/**），
-- 已迁到 `sql/demo_init_data.sql`（**可选执行**：不需要示例模块的部署可跳过该文件，
-- 跳过则前端没有「示例模块」目录，其余功能不受影响）。
-- 迁出原因：**系统模块的初始化数据不应该引用另一个模块的实现**（裁剪掉 demo 模块的部署
-- 没有 demoJobService Bean，却因为这份种子拿到了「默认启用」的示例任务，装完就天天失败）。

-- ----------------------------
-- 系统管理菜单
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1000, 0, '系统管理', 10, 'LAYOUT', 'system', 'M', 1, '', 0, 'setting-1', '系统管理目录'),
(1001, 1000, '用户管理', 1, '/system/user/index', 'systemUser', 'C', 1, 'system:user:view', 0, 'user-circle', '用户管理菜单'),
(1002, 1000, '角色管理', 2, '/system/role/index', 'systemRole', 'C', 1, 'system:role:view', 0, 'usergroup', '角色管理菜单'),
(1003, 1000, '菜单管理', 3, '/system/menu/index', 'systemMenu', 'C', 1, 'system:menu:view', 0, 'view-list', '菜单管理菜单'),
(1004, 1000, '部门管理', 4, '/system/dept/index', 'systemDept', 'C', 1, 'system:dept:view', 0, 'sitemap', '部门管理菜单'),
(1005, 1000, '字典管理', 5, '/system/dict/index', 'systemDict', 'C', 1, 'system:dict:view', 0, 'book', '字典管理菜单'),
(1006, 1000, '日志管理', 6, '/system/log/index', 'systemLog', 'C', 1, 'system:log:view', 0, 'rollfront', '日志管理菜单'),
(1008, 1000, '代码生成', 8, '/system/codegen/index', 'systemCodegen', 'C', 1, 'system:codegen:view', 0, 'code', '代码生成器'),
(1009, 1000, '通知管理', 9, '/system/notification/index', 'systemNotification', 'C', 1, 'system:notification:view', 0, 'notification', '通知管理菜单');

-- 代码生成按钮（权限位）
-- 缺 F 行的后果（2026-10-14 复核）：超管的 permissions 只由 F 型菜单的 perms 组成
-- （LoginUtil.getPermList 对角色 1 的特殊分支），而前端路由守卫校验 meta.perm
-- （system:codegen:view）→ 菜单点开即被打回 404；CodeGenController 的生成类接口
-- 也需要 system:codegen:generate 才能被非超管角色授权。
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2310, 1008, '生成器查询', 1, '', NULL, 'F', 1, 'system:codegen:view', 0, '#', '代码生成页面访问（查询 / 预览类接口）'),
(2311, 1008, '执行生成', 2, '', NULL, 'F', 1, 'system:codegen:generate', 0, '#', '执行代码生成（生成类接口）');

-- 定时任务菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1010, 1000, '定时任务', 10, '/system/job/index', 'systemJob', 'C', 1, 'system:job:view', 0, 'time', '定时任务管理');

-- 定时任务按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1901, 1010, '任务查询', 1, '', NULL, 'F', 1, 'system:job:view', 0, '#', ''),
(1902, 1010, '任务新增', 2, '', NULL, 'F', 1, 'system:job:add', 0, '#', ''),
(1903, 1010, '任务修改', 3, '', NULL, 'F', 1, 'system:job:update', 0, '#', ''),
(1904, 1010, '任务删除', 4, '', NULL, 'F', 1, 'system:job:delete', 0, '#', ''),
(1905, 1010, '任务执行', 5, '', NULL, 'F', 1, 'system:job:trigger', 0, '#', ''),
(1906, 1010, '日志清空', 6, '', NULL, 'F', 1, 'system:job:log:clean', 0, '#', '');

-- 参数配置菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1007, 1000, '参数配置', 7, '/system/config/index', 'systemConfig', 'C', 1, 'system:config:view', 0, 'tools', '参数配置菜单');

-- 参数配置按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1701, 1007, '参数查询', 1, '', NULL, 'F', 1, 'system:config:view', 0, '#', ''),
(1702, 1007, '参数新增', 2, '', NULL, 'F', 1, 'system:config:add', 0, '#', ''),
(1703, 1007, '参数修改', 3, '', NULL, 'F', 1, 'system:config:update', 0, '#', ''),
(1704, 1007, '参数删除', 4, '', NULL, 'F', 1, 'system:config:delete', 0, '#', '');

-- ----------------------------
-- 初始参数配置数据
-- ----------------------------
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `system_builtin`, `remark`) VALUES
(1, '系统名称', 'system.name', '疾风框架', 1, '系统内置参数，展示于页面标题/侧边栏'),
(2, '是否开启上传拦截', 'system.upload.intercept', 'true', 1, '系统内置参数，示例参数');
-- 在线用户菜单
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1011, 1000, '在线用户', 11, '/system/online/index', 'systemOnline', 'C', 1, 'system:online:view', 0, 'user-circle', '在线用户菜单');

-- 在线用户按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2001, 1011, '在线查询', 1, '', NULL, 'F', 1, 'system:online:view', 0, '#', ''),
(2002, 1011, '强制下线', 2, '', NULL, 'F', 1, 'system:online:kick', 0, '#', '');

-- 用户管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1101, 1001, '用户查询', 1, '', NULL, 'F', 1, 'system:user:view', 0, '#', ''),
(1102, 1001, '用户新增', 2, '', NULL, 'F', 1, 'system:user:add', 0, '#', ''),
(1103, 1001, '用户修改', 3, '', NULL, 'F', 1, 'system:user:update', 0, '#', ''),
(1104, 1001, '用户删除', 4, '', NULL, 'F', 1, 'system:user:delete', 0, '#', ''),
(1105, 1001, '重置密码', 5, '', NULL, 'F', 1, 'system:user:resetPassword', 0, '#', ''),
(1106, 1001, '解锁账号', 6, '', NULL, 'F', 1, 'system:user:unlock', 0, '#', '');

-- 角色管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1201, 1002, '角色查询', 1, '', NULL, 'F', 1, 'system:role:view', 0, '#', ''),
(1202, 1002, '角色新增', 2, '', NULL, 'F', 1, 'system:role:add', 0, '#', ''),
(1203, 1002, '角色修改', 3, '', NULL, 'F', 1, 'system:role:update', 0, '#', ''),
(1204, 1002, '角色删除', 4, '', NULL, 'F', 1, 'system:role:delete', 0, '#', '');

-- 菜单管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1301, 1003, '菜单查询', 1, '', NULL, 'F', 1, 'system:menu:view', 0, '#', ''),
(1302, 1003, '菜单新增', 2, '', NULL, 'F', 1, 'system:menu:add', 0, '#', ''),
(1303, 1003, '菜单修改', 3, '', NULL, 'F', 1, 'system:menu:update', 0, '#', ''),
(1304, 1003, '菜单删除', 4, '', NULL, 'F', 1, 'system:menu:delete', 0, '#', '');

-- 部门管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1401, 1004, '部门查询', 1, '', NULL, 'F', 1, 'system:dept:view', 0, '#', ''),
(1402, 1004, '部门新增', 2, '', NULL, 'F', 1, 'system:dept:add', 0, '#', ''),
(1403, 1004, '部门修改', 3, '', NULL, 'F', 1, 'system:dept:update', 0, '#', ''),
(1404, 1004, '部门删除', 4, '', NULL, 'F', 1, 'system:dept:delete', 0, '#', '');

-- 字典管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1501, 1005, '字典查询', 1, '', NULL, 'F', 1, 'system:dict:view', 0, '#', ''),
(1502, 1005, '字典新增', 2, '', NULL, 'F', 1, 'system:dict:add', 0, '#', ''),
(1503, 1005, '字典修改', 3, '', NULL, 'F', 1, 'system:dict:update', 0, '#', ''),
(1504, 1005, '字典删除', 4, '', NULL, 'F', 1, 'system:dict:delete', 0, '#', '');

-- 日志管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1601, 1006, '日志查询', 1, '', NULL, 'F', 1, 'system:log:view', 0, '#', ''),
(1602, 1006, '日志清理', 2, '', NULL, 'F', 1, 'system:log:delete', 0, '#', '清理历史操作日志（后端 SysLogController#clean 校验此权限；缺这一行则普通管理员永远无法被授予该权限）');

-- 通知管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1801, 1009, '通知查询', 1, '', NULL, 'F', 1, 'system:notification:view', 0, '#', ''),
(1802, 1009, '通知新增', 2, '', NULL, 'F', 1, 'system:notification:add', 0, '#', ''),
(1803, 1009, '通知修改', 3, '', NULL, 'F', 1, 'system:notification:update', 0, '#', ''),
(1804, 1009, '通知删除', 4, '', NULL, 'F', 1, 'system:notification:delete', 0, '#', ''),
(1805, 1009, '通知发送', 5, '', NULL, 'F', 1, 'system:notification:send', 0, '#', ''),
(1806, 1009, '模板查询', 6, '', NULL, 'F', 1, 'system:notification:template:view', 0, '#', ''),
(1807, 1009, '模板新增', 7, '', NULL, 'F', 1, 'system:notification:template:add', 0, '#', ''),
(1808, 1009, '模板修改', 8, '', NULL, 'F', 1, 'system:notification:template:edit', 0, '#', ''),
(1809, 1009, '模板删除', 9, '', NULL, 'F', 1, 'system:notification:template:delete', 0, '#', '');

-- ----------------------------
-- 系统监控菜单（后端 SysMonitorController：/sys/monitor）
-- 页面与路由均已存在（jffast-ui/src/pages/system/monitor/index.vue），此前缺菜单种子 ⇒ 无入口
-- 段位说明：1012/1013/1014 与 21xx/22xx/23xx 已被 sys_post / sys_task / sys_file 三个
-- 「可选模块」占用，且它们用 INSERT IGNORE 导入——撞号时**后导入的一方被静默丢弃**，
-- 表现为菜单永久缺失且没有任何报错。故本文件的三个新页面用 1015-1017，按钮用 24xx-26xx。
-- 存量库若已按旧号导入过（系统监控=1012 等），手工把这三段改到新号，再重跑
-- sys_post.sql / sys_task.sql / sys_file.sql 补回被丢弃的三个模块菜单。
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1015, 1000, '系统监控', 15, '/system/monitor/index', 'systemMonitor', 'C', 1, 'system:monitor:view', 0, 'chart-bubble', '系统监控菜单');

-- 系统监控按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2401, 1015, '监控查看', 1, '', NULL, 'F', 1, 'system:monitor:view', 0, '#', '');

-- ----------------------------
-- 缓存管理菜单（后端 SysCacheController：/sys/cache）
-- 页面与路由均已存在（jffast-ui/src/pages/system/cache/index.vue），此前缺菜单种子 ⇒ 无入口
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1016, 1000, '缓存管理', 16, '/system/cache/index', 'systemCache', 'C', 1, 'system:cache:view', 0, 'folder-open', '缓存管理菜单');

-- 缓存管理按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2501, 1016, '缓存查询', 1, '', NULL, 'F', 1, 'system:cache:view', 0, '#', ''),
(2502, 1016, '缓存删除', 2, '', NULL, 'F', 1, 'system:cache:delete', 0, '#', '');

-- ----------------------------
-- OAuth2 接入配置菜单（后端 OAuth2ClientConfigController：/oauth2/client/config）
-- 页面与路由均已存在（jffast-ui/src/pages/system/oauth2-client/index.vue），此前缺菜单种子 ⇒ 无入口
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(1017, 1000, 'OAuth2接入配置', 17, '/system/oauth2-client/index', 'systemOAuth2Client', 'C', 1, 'system:oauth2client:view', 0, 'link', 'OAuth2客户端接入配置菜单');

-- OAuth2 接入配置按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(2601, 1017, '接入查询', 1, '', NULL, 'F', 1, 'system:oauth2client:view', 0, '#', ''),
(2602, 1017, '接入新增', 2, '', NULL, 'F', 1, 'system:oauth2client:add', 0, '#', ''),
(2603, 1017, '接入修改', 3, '', NULL, 'F', 1, 'system:oauth2client:update', 0, '#', ''),
(2604, 1017, '接入删除', 4, '', NULL, 'F', 1, 'system:oauth2client:delete', 0, '#', ''),
-- 读取第三方 access token：敏感权限，默认不授予（后端 OAuth2ClientConfigController#token 校验此权限；
-- 缺这一行则该端点对普通管理员永远不可达，application.yml 里「持有者即可批量导出接入方凭据」的说明也会落空）
(2605, 1017, '读取接入凭据', 5, '', NULL, 'F', 1, 'system:oauth2client:token', 0, '#', '读取第三方 access token（敏感，建议仅授予可信管理员）');

-- ----------------------------
-- 超级管理员角色（ID=1 与 BaseConstant.ROLE_ID_SUPER_ADMIN_LONG 一致）
-- ----------------------------
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_key`, `role_name`, `is_open`, `data_scope`, `remark`) VALUES
(1, NULL, 'super_admin', '超级管理员', 1, 1, '超级管理员，拥有所有权限');

-- 普通角色
INSERT INTO `sys_role` (`id`, `tenant_id`, `role_key`, `role_name`, `is_open`, `data_scope`, `remark`) VALUES
(2, NULL, 'common', '普通用户', 1, 4, '普通用户，仅本人数据');

-- ----------------------------
-- 超级管理员用户（此行为占位密码 "admin"，启动时由 DataInitializer 替换为
-- jffast.system.init-password 的加密值；未配置时默认 test1234，prod 必须显式注入）
-- ----------------------------
INSERT INTO `sys_user` (`id`, `tenant_id`, `user_name`, `real_name`, `nick_name`, `dept_id`, `password`, `salt`, `avatar`, `phone`, `email`, `state`, `role_id`, `login_date`, `update_pwd_time`) VALUES
(1, NULL, 'admin', '管理员', '超级管理员', NULL, 'admin', NULL, NULL, NULL, NULL, 1, '1', NULL, NULL);

-- ----------------------------
-- 超级管理员 - 用户角色关联
-- ----------------------------
INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- ----------------------------
-- 超级管理员无需角色菜单关联（通过 orRole = BaseConstant.ROLE_SUPER_ADMIN 自动拥有所有权限）
-- 普通角色如需分配菜单权限，在此添加 sys_role_menu 关联记录
-- ----------------------------

-- ----------------------------
-- 安全设置初始数据
-- ----------------------------
INSERT INTO `sys_safe` (`id`, `pwd_cycle`, `pwd_login_limit`, `idle_time_setting`) VALUES
(1, 0, 0, 0);

-- ----------------------------
-- 定时任务初始数据
-- ----------------------------
-- ⚠️ 这里**只放系统模块自身的任务**。
-- 示例任务（`demoJobService.cleanExpiredData` / `demoJobService.generateReport(daily)`）属于
-- **jffast-demo 模块**，见 `sql/demo_init_data.sql`，且**两条都默认 status=0（暂停）**：
--   · 若旧种子设成 status=1 启用，而部署未包含 `demoJobService` Bean
--     ⇒ 装完就「每天 02:00 失败一条/两条」，且只写进 `sys_job_log`、服务日志无痕。
--   · 想体验示例任务：先执行 `sql/demo_init_data.sql`，再到「系统管理 → 定时任务」点启用
--     （启用前确认部署包含 jffast-demo 模块）。
