-- =============================================
-- 示例模块（jffast-demo）初始数据：菜单 + 示例定时任务
-- =============================================
--
-- 【为什么单独成文件】
--   本文件描述的全是 **jffast-demo 模块** 的东西：
--     · 菜单 200-215 → 前端页面 jffast-ui/src/pages/demo/**（示例管理 / 组件示例）
--     · 示例任务 1-2 → 后端 Bean jffast-demo 的 @Service("demoJobService")
--   而它们此前写在 `system_init_data.sql`（**系统模块**的种子）里 —— 系统模块的初始化数据
--   不应该引用**另一个模块**的实现：若部署裁剪掉 demo 模块，旧种子仍会插入依赖
--   `demoJobService` 的启用任务 ⇒ 「装完就每天 02:00 失败一次」，
--   且异常只写进 `sys_job_log`（status=2），服务日志无痕，极易被误当成自己的配置问题。
--
-- 【本文件的两条约定】
--   1. 示例任务 1、2 一律 **status=0（暂停）** —— 需要体验时到「系统管理 → 定时任务」点启用即可。
--      ⚠️ 启用前请确认当前部署**包含 jffast-demo 模块**（不包含则没有任何服务能执行该任务）。
--   2. 本文件是**可选**的：不需要示例模块的部署可以**跳过**（跳过则前端没有「示例模块」目录，
--      其余功能不受影响）。
--
-- 【执行方式】
--   mysql -u<user> -p <db> < sql/demo_init_data.sql
--
-- ⚠️ 刻意使用**普通 INSERT**（不是 `INSERT IGNORE`）：重复执行/ID 段冲突时会**显式报错**。
--    菜单 ID 段冲突若被 `INSERT IGNORE` 掩盖，会表现为前端路由缺失、页面全白。
--    分配新段前先查实际占用（交付工具集内附菜单占用统计脚本）
--    本文件占用：菜单 200-202/204、按钮 211-215/220-223、任务 1-2。

SET NAMES utf8mb4;

-- ----------------------------
-- 示例模块菜单
-- ----------------------------
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(200, 0, '示例模块', 2, 'LAYOUT', 'demo', 'M', 1, '', 0, 'view-module', '示例模块目录'),
(201, 200, '示例管理', 1, '/demo/demoExample/index', 'demoExample', 'C', 1, 'demo:example:view', 0, 'view-module', '示例管理菜单'),
(202, 200, '组件示例', 2, '/demo/componentShowcase/index', 'components', 'C', 1, 'demo:components:view', 0, 'code', '组件示例菜单'),
(204, 200, '租户隔离示例', 3, '/demo/demoTenantExample/index', 'demoTenantExample', 'C', 1, 'demo:tenant-example:view', 0, 'building', '租户隔离示例'),
-- 请假申请：后端 LeaveApplicationController 与前端页面都在，此前没有任何菜单入口 ⇒ 功能等于半残
(205, 200, '请假申请', 4, '/demo/leaveApplication/index', 'demoLeaveApplication', 'C', 1, 'demo:leave:view', 0, 'calendar', '请假申请菜单');

-- 示例模块按钮
INSERT INTO `sys_menu` (`id`, `parent_id`, `name`, `sort`, `url`, `path`, `type`, `state`, `perms`, `visible`, `icon`, `remark`) VALUES
(211, 201, '示例查询', 1, '', NULL, 'F', 1, 'demo:example:view', 0, '#', ''),
(212, 201, '示例新增', 2, '', NULL, 'F', 1, 'demo:example:add', 0, '#', ''),
(213, 201, '示例修改', 3, '', NULL, 'F', 1, 'demo:example:update', 0, '#', ''),
(214, 201, '示例删除', 4, '', NULL, 'F', 1, 'demo:example:delete', 0, '#', ''),
-- 导出 / 导入：后端 DemoExampleController 校验这两个权限码，此前种子缺失 ⇒ 普通管理员点了必 403
(216, 201, '示例导出', 5, '', NULL, 'F', 1, 'demo:example:export', 0, '#', ''),
(217, 201, '示例导入', 6, '', NULL, 'F', 1, 'demo:example:import', 0, '#', ''),
(215, 202, '组件示例查看', 1, '', NULL, 'F', 1, 'demo:components:view', 0, '#', ''),
(220, 204, '租户示例查询', 1, '', NULL, 'F', 1, 'demo:tenant-example:view', 0, '#', ''),
(221, 204, '租户示例新增', 2, '', NULL, 'F', 1, 'demo:tenant-example:add', 0, '#', ''),
(222, 204, '租户示例修改', 3, '', NULL, 'F', 1, 'demo:tenant-example:edit', 0, '#', ''),
(223, 204, '租户示例删除', 4, '', NULL, 'F', 1, 'demo:tenant-example:delete', 0, '#', ''),
-- 请假申请按钮：与后端 LeaveApplicationController 的四个权限码逐一对应
(230, 205, '请假查询', 1, '', NULL, 'F', 1, 'demo:leave:view', 0, '#', ''),
(231, 205, '请假新增', 2, '', NULL, 'F', 1, 'demo:leave:add', 0, '#', ''),
(232, 205, '请假修改', 3, '', NULL, 'F', 1, 'demo:leave:edit', 0, '#', ''),
(233, 205, '请假删除', 4, '', NULL, 'F', 1, 'demo:leave:delete', 0, '#', '');

-- ----------------------------
-- 示例定时任务（**两条都默认暂停**）
-- ----------------------------
-- `invoke_target` 格式为「Bean 名.方法名」（带参为 `bean.method(param)`），由 jffast-starter-job
-- 反射调用：`JobInvokeUtil` → `SpringContextHolder.getBean(beanName)` —— **找不到 Bean 就抛
-- NoSuchBeanDefinitionException**，只会写进 `sys_job_log`，不影响服务启动。
-- 这两个目标 Bean 都在 jffast-demo 模块（`cn.jfspeed.demo.service.DemoJobService`）：
--   · `cleanExpiredData()` 无参、只打日志（示例，不做真实清理）
--   · `generateReport(reportType)` 带一个 String 参数
INSERT INTO `sys_job` (`id`, `job_name`, `job_group`, `invoke_target`, `cron_expression`, `misfire_policy`, `concurrent`, `status`, `remark`) VALUES
(1, '清理过期数据', 'DEFAULT', 'demoJobService.cleanExpiredData', '0 0 2 * * ?', '3', 0, 0, '示例任务（默认暂停）：启用前请确认部署包含 jffast-demo 模块'),
(2, '生成日报', 'DEFAULT', 'demoJobService.generateReport(daily)', '0 0 6 * * ?', '3', 0, 0, '示例任务（默认暂停）：启用前请确认部署包含 jffast-demo 模块');
