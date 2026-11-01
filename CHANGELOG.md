# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [1.0.0] - 2026-11-01

### 新增

- 可插拔 Starter 体系：mybatis / web / security / redis / log / file / excel / social / job / flowable / oauth2-client / demo-mode 共 12 个 Starter。
- 系统管理：用户、角色、菜单、部门、岗位、字典、操作日志、通知中心、文件管理、异步任务。
- 代码生成器：按表结构生成前后端 CRUD 代码，支持树表。
- 工作流：Flowable 8 BPMN 2.0，流程部署 / 发起 / 审批 / 历史，审批人 SPI（user / role / dept / initiator）。
- 定时任务：Quartz + `@JfFastJob` 注解式归属，`sys_job.service_name` 支持多服务共库去重。
- 文件存储：本地 / MinIO / 阿里云 OSS / 腾讯云 COS，上传登记 `sys_file`，支持类型与大小白名单。
- Excel 与异步任务：FastExcel 导入导出、模板下载、异步进度与结果文件下载（`sys_task`）。
- 通知中心：站内信 + 邮件 / 短信通道 + 模板管理。
- AI 知识库：知识库 / 文档 / 对话存储管理（`jffast-ai`）。
- 国际化：框架级消息国际化（Accept-Language / `MessageUtil`）。
- 系统监控与缓存管理：JVM / OS / 磁盘、Redis 信息与缓存键管理。
- 安全能力：RSA 登录加密 + BCrypt(12)、XSS 过滤、接口限流、分布式锁、防重复提交、登录失败计数与验证码。
- 多数据源：`@DS` 注解动态切换（默认关闭）。
- 用户与扩展能力：用户↔岗位多对多绑定、社交账号绑定（GitHub / Gitee / Microsoft）、OAuth2 客户端接入、演示模式 Starter。

### 变更

- 失败响应的 `data` 字段固定承载 traceId，不再承载业务数据；业务错误信息走 `msg`。
- WebSocket `allowed-origins` 留空时**只放行同源握手**；跨源必须显式列出来源。
- 用户岗位绑定 `postList` 采用三态语义：缺席 = 不修改、`[]` = 清空、非空 = 整组替换；岗位仍被用户绑定时禁止删除。
- 生产环境 `spring.profiles.active=prod` 下接口文档与会话日志默认关闭，RSA 密钥与初始口令必须显式注入。

### 修复

- 通知通道按类型去重（重复实现只保留第一个并告警）；短信网关异常报文不再回显响应体，投递目标日志脱敏。
- Sa-Token JSON 反序列化白名单补齐 `SysPost`，避免绑定岗位的用户登录后静默失效。
- 文件删除、部门层级重算、工作流审批人角色解析、用户导出数据范围等缺陷修复。

### 安全

- 初始管理员口令支持 `JFFAST_INIT_PASSWORD` 覆盖；dev / test 启动时自动生成一次性 RSA 密钥对。
- 生产 profile 缺 RSA 密钥或初始口令时启动失败（fail-fast）。
- `/upload/**` 免登录可读、字典基础读接口登录即可等取舍已写入 `SECURITY.md`，部署前请阅读。
