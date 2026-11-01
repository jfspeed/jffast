# 版本亮点 · jffast 1.0.0（首发）

> 面向使用者与接入方：本包首发包含的能力与选型价值。逐条变更见 [CHANGELOG.md](../CHANGELOG.md)，接入操作见本文「接入须知」与「验证要点」。

## 一句话摘要

首发即采用 `jffast` 品牌与 `cn.jfspeed` 坐标，Base 包含演示模式 Starter、AI 知识模块、WebSocket 业务扩展点、注解式定时任务归属，以及一轮开源评审沉淀的安全与测试门禁。

## 亮点

### 1 品牌与坐标（接入必读）

- 全部 Java 包根使用 `cn.jfspeed`，Maven 坐标 / 模块名使用 `jffast-*`，API 前缀 `/jffast-api`，配置根键 `jffast:`，环境变量 `JFFAST_*`。
- 意义：对外只有一个坐标口径，接入方不需要理解历史命名。

### 2 演示模式 Starter（`jffast-starter-demo-mode`）

- 面向「线上给客户演示」场景：**写操作可正常交互但不落库**，演示后环境零污染。
- 通过配置开关启用，业务代码无需分支判断；详见 [docs/demo-mode.md](demo-mode.md)。

### 3 工作流：审批人解析优先级明确（`jffast-starter-flowable`）

- `AssigneeTaskListener` 支持按**任务级 BPMN 扩展属性**（`flowable:assigneeType/assigneeValue`）解析审批人，逐任务生效；未配置时回退流程变量。
- 与可视化流程设计器产出的属性对齐，并补了「引擎必须保留扩展属性」的契约测试。
- 详见 [docs/flowable.md](flowable.md)。

### 4 WebSocket 能力

- 站内通知端点由自动配置登记，业务侧实现 `WebSocketEventHandler` SPI 即可收发消息回调，不必改框架。
- 握手支持子协议携带 token（保留 query 参数兜底）。
- `allowed-origins` 留空时只放行同源握手；跨源必须显式列出来源。

### 5 定时任务注解式归属

- `@JfFastJob` + `sys_job.service_name` 列 + Redis 执行锁：**多服务共库时同一任务只被一个服务调度**，避免重复执行。
- 执行失败可降级、孤儿触发器有反向对账、锁与字段自动填充健壮性整改。

### 6 安全与鉴权基线

- 登录前置的验证码接口纳入免登录白名单，同时白名单本身可配置。
- 端点权限门禁（权限注解补全 + 规范）、角色 ID 保存回写、数据权限审计上下文。
- Redis `KEYS` 改 `SCAN`、大数据量分页改为库内分页、分布式锁切面反射签名修复。
- 初始管理员口令改为可配置；RSA 公私钥由环境变量注入，部署侧必须成对配置。

### 7 质量与测试

- 魔法错误码收敛为 `IErrorCode` 枚举，错误码段位成为跨仓契约。
- 新增鉴权错误码、权限、密码、路径穿越等测试；JaCoCo 覆盖率门禁与依赖漏洞审查进入 CI。
- 前端仓库配套 Vitest 单测与 Playwright E2E 约定。

### 8 AI 与业务模块

- `jffast-ai` 模块纳入模块清单：知识库 / 分块等能力承载点。
- `jffast-demo`：示例管理支持图片上传与状态切换、`/upload` 静态映射、请假与租户示例数据。

### 9 OAuth2 客户端接入 Starter

- `jffast-starter-oauth2-client`：第三方 OAuth2 登录接入与系统配置页，控制器条件装配（未配置不加载），令牌缓存随配置变更清理。

## 接入须知

| 项 | 说明 |
|------|------|
| 包根 / 坐标 / API 前缀 | `cn.jfspeed` / `jffast-*` / `/jffast-api`；接入方按此配置依赖与网关转发 |
| `sys_job.service_name` | 建表语句已内建；自定义任务请补归属服务，否则按当前服务处理 |
| 初始管理员口令 | 默认公开测试值仅限本地体验；prod 必须注入 `JFFAST_INIT_PASSWORD` |
| 错误码 | 统一使用 `IErrorCode` 枚举与段位表，自定义错误码不要占用框架段位 |
| RSA 密钥 | 前后端必须成对；prod 注入 `RSA_PRIVATE_KEY` / `RSA_PUBLIC_KEY`（前端 `VITE_RSA_PUBLIC_KEY`） |

## 验证要点

1. `mvn clean verify` 全绿（含 JaCoCo 门禁）。
2. 启动 `jffast-boot`：`/jffast-api/sys/needCaptcha` 未登录可访问、登录可拿到 token。
3. 打开演示模式开关后做一次写操作 → 数据不落库、界面反馈正常。
4. 发起一条流程 → 校验任务审批人来自任务级属性；删掉属性后回退流程变量。
5. 建立 WebSocket 连接（子协议带 token）→ 触发一次站内通知能收到推送。
