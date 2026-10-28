# 版本亮点 · jffast 1.1.0

> 面向使用者与接入方：本包相对 1.0.0 的能力变化与选型价值。逐条变更见 [CHANGELOG.md](../CHANGELOG.md)，升级操作见本文「升级须知」与「验证要点」。

## 一句话摘要

统一为 `jffast` 品牌与 `cn.jfspeed` 坐标后，Base 补齐了**演示模式 Starter、AI 知识模块、WebSocket 业务扩展点、注解式定时任务归属**，并把一轮开源评审沉淀的安全与测试门禁固化进代码与 CI。

## 亮点

### 1 品牌与坐标统一（升级必读）

- 全部 Java 包根改为 `cn.jfspeed`，Maven 坐标 / 模块名改为 `jffast-*`，API 前缀 `/jffast-api`，配置根键 `jffast:`，环境变量 `JFFAST_*`。
- 意义：一个可对外分发的开源品牌；一次全局替换即可对接，无需理解历史命名。

### 2 演示模式 Starter（`jffast-starter-demo-mode`，新增）

- 面向「线上给客户演示」场景：**写操作可正常交互但不落库**，演示后环境零污染。
- 通过配置开关启用，业务代码无需分支判断；详见 [docs/demo-mode.md](demo-mode.md)。

### 3 工作流：审批人解析优先级明确（`jffast-starter-flowable`）

- `AssigneeTaskListener` 支持按**任务级 BPMN 扩展属性**（`flowable:assigneeType/assigneeValue`）解析审批人，逐任务生效；未配置时回退流程变量，既有接入不受影响。
- 与可视化流程设计器产出的属性对齐，并补了「引擎必须保留扩展属性」的契约测试。
- 详见 [docs/flowable.md](flowable.md)。

### 4 WebSocket 能力补齐

- 修复站内通知端点从未注册的问题（自动配置登记）。
- 新增 `WebSocketEventHandler` SPI：业务侧实现接口即可收发消息回调，不必改框架。
- 握手支持子协议携带 token（保留 query 参数兜底），浏览器与网关场景都好接。
- `ServerEndpointExporter` 限定为 servlet web 应用注册，非 servlet 上下文启动不再失败。

### 5 定时任务改为注解式声明归属

- `@JfFastJob` + `sys_job.service_name` 列 + Redis 执行锁：**多服务共库时同一任务只被一个服务调度**，避免重复执行。
- 执行失败可降级、孤儿触发器有反向对账、锁与字段自动填充健壮性整改。

### 6 安全与鉴权加固（开源评审沉淀）

- 登录前置的验证码接口纳入免登录白名单，同时白名单本身可配置。
- 端点权限门禁（权限注解补全 + 规范）、角色 ID 保存回写、数据权限审计上下文。
- Redis `KEYS` 改 `SCAN`、大数据量分页改为库内分页、分布式锁切面反射签名修复（此前表现为「保存报系统繁忙」）。
- 初始管理员口令改为可配置；RSA 公私钥上移到 `application.yml` 由环境变量注入，部署侧必须成对配置（此前散落在 profile 中导致容器部署解密失败）。

### 7 质量与测试

- 魔法错误码收敛为 `IErrorCode` 枚举，错误码段位成为跨仓契约。
- 新增鉴权错误码、权限、密码、路径穿越等测试；JaCoCo 覆盖率门禁与依赖漏洞审查进入 CI。
- 文档化 Vitest 单测约定（前端仓库配套）。

### 8 AI 与业务模块

- `jffast-ai` 模块正式纳入模块清单与文档口径（知识库/分块等能力承载点）。
- `jffast-demo`：示例管理支持图片上传与状态切换、`/upload` 静态映射修复、请假与租户示例数据补齐。

### 9 OAuth2 客户端接入 Starter

- 新增 `jffast-starter-oauth2-client`：第三方 OAuth2 登录接入与系统配置页，控制器条件装配（未配置不加载），令牌缓存随配置变更清理。

## 升级须知

| 变更 | 影响 | 处理 |
|------|------|------|
| 包根 / 坐标 / API 前缀 / 配置根键重命名 | **破坏性** | 全局替换 import、依赖坐标、`JFFAST_*` 环境变量与网关转发路径 |
| `sys_job` 新增 `service_name` | 任务无归属 | 由 `sql/system_sql.sql` 建表语句直接内建；存量数据归属留空按本服务处理 |
| 错误码由魔法值改枚举 | 自定义错误码可能重号 | 对照 `docs/开发规范.md` 段位表校正 |
| 初始密码改为可配置 | 部署脚本需传参 | 见部署文档环境变量清单 |

## 验证要点

1. `mvn clean verify` 全绿（含 JaCoCo 门禁）。
2. 启动 `jffast-boot`：`/jffast-api/sys/needCaptcha` 未登录可访问、登录可拿到 token。
3. 打开演示模式开关后做一次写操作 → 数据不落库、界面反馈正常。
4. 发起一条流程 → 校验任务审批人来自任务级属性；删掉属性后回退流程变量。
5. 建立 WebSocket 连接（子协议带 token）→ 触发一次站内通知能收到推送。
