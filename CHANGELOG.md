# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

> `1.0.0` 之后的一批提交尚未发版，按类别摘要如下；完整清单以 git 历史为准。

### Security

- 登录密码加密改为 RSA-OAEP（SHA-256 摘要 + MGF1-SHA1、空 label）：前后端必须同步升级，已注入的 2048 位密钥对无需重新生成。
- 下线 `/upload/**` 匿名静态直读：上传统一登记 `sys_file`（fail-closed，登记失败即删除已落盘文件）；读取收口为 `GET /sys/file/download/{id}`（登录 + 归属校验）与 `GET /sys/file/view/{id}?ticket=...`（短期 HMAC 签名，校验 TTL / 文件 id / 租户一致，响应带 nosniff 与 CSP sandbox）；文件引用统一用 `file:{id}`，经 `/sys/file/signed-url/{id}` 换发签名地址。
- 错误响应不再把雪花短 ID 塞进 `data`；链路 traceId 统一由响应头 `X-Trace-Id` 下发。
- 全局异常处理器取请求 URI 改为判空的 `safeUri()`，无 servlet 请求上下文时不再抛 NPE 吞掉原始异常。

### Added

- 通知模板与分组、异步任务 / 执行日志按 `tenant_id` 落库隔离；`TenantScopeBridge` 租户上下文桥接基础设施在 Base 定义。
- Flowable 相关分页响应统一为 `PageRes`。
- 登录数据源 SPI 化：登录用户来源改为可插拔扩展点。
- 登录滑块验证码（tianai-captcha 1.5.5，机制二）：登录失败达到阈值后前端拉起滑块拼图，`/sys/slider/gen` / `/sys/slider/check` 匿名端点按来源 IP 分桶限流，校验通过换发一次性登录凭证（TTL 60s，消费即删防重放）；`jffast.captcha.slider-enabled=false` 可整体关闭并回落纯图片验证码。

### Fixed

- `jffast-starter-file` 自动装配条件注解修复。
- `DataInitializer` 按「有意全量声明」口径整理初始化种子。
- 启动校验文案与注释中的 `/upload/**` 免登录静态口径更新为受控下载 / 签名直显。

### Changed

- `1.0.0` 条目中「失败响应 `data` 固定承载 traceId」与「`/upload/**` 免登录可读取舍」两处口径已按上文修正；`SECURITY.md`、`README.md` 同步更新。

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

- 失败响应只返回 `code` / `message`；链路 traceId 统一由响应头 `X-Trace-Id` 下发，`data` 不再承载追踪标识。
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
- 字典基础读接口登录即可等取舍，以及当前文件访问安全模型已写入 `SECURITY.md`，部署前请阅读。
