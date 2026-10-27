# jffast 项目规则

> 本文件供 AI 编码助手参考。最后更新：2026-09-24  
> 结构：协作规范 → 后端规范 → 项目信息（三块带标记，块内以外部模板源为准可刷新）。

<!-- BEGIN: 协作规范 -->

# jffast 协作规范（通用）

> 本节与具体技术栈无关，任何项目都适用。
> 详细编码约定以 `docs/开发规范.md` 为准，本节只定协作纪律。
>
> **术语「私有信息」（统称，两类都算）**
> - **身份类**：姓名、个人邮箱、手机号、账号名、本机用户名
> - **环境类**：本机绝对路径、私有端口、私有项目名、内部域名、内部机制（如同步方式）
>
> 下文一律用「私有信息」指代这两类，不再细分称呼。
> 需要落地的**实际值**一律放 `LOCAL.md`（该文件不入库，必须列入 `.gitignore`）；`AGENTS.md` 里只保留引用，不写值。

## 一、开发原则：先分析再实现

收到功能需求后，**必须先完成需求分析，再动手写代码**。分析至少覆盖：

1. **功能边界**：包含什么、不包含什么，隐含的边界条件
2. **影响范围**：涉及哪些模块、文件、配置
3. **数据模型**：新增/变更的表与字段、与现有表的关系、是否需要隔离维度
4. **兼容性**：是否影响现有功能、是否需要向后兼容或数据迁移
5. **配置方式**：配置项放哪、是否需要开关控制
6. **交互与权限**：入口位置、权限怎么控制、是否需要适配多种模式
7. **文档同步**：按第二节确认需要更新哪些文档

**禁止**：用户说一个需求就立刻写代码。先输出简洁的分析结论（要点列表），确认后再实现。
分析中发现需求有歧义或遗漏，**必须主动提出**，不得自行假设。

## 二、文档同步规则

功能变更时必须同步更新对应文档，确保文档不落后于代码。

### 2.1 文档归属

| 归属 | 位置 | 说明 |
|------|------|------|
| 权威编码规范 | `docs/开发规范.md` | 编码约定唯一权威，其他文件只引用不复制 |
| 功能级文档 | `docs/` | 归属对应模块，按模块分目录 |
| 使用者文档 | 独立文档站（本仓不含使用者文档目录） | 对外可见的功能 / 模块使用说明 |
| 部署文档 | `deploy/` | 部署与运维说明 |
| 项目 README | 仓库根 `README.md` | 项目级说明 |
| AI 规则文件 | 仓库根 `AGENTS.md` | 每项目一份，统一命名与结构 |

> **元文档归属红线**：跨项目、跨环境、验证 / 排障类元文档只写一处，禁止在各子仓库重复存放 —— 多份副本必然漂移，且换会话时散落各处难以复用。

### 2.2 同步规则

| 变更类型 | 需更新的文档 |
|----------|--------------|
| 新增 / 修改 / 删除功能模块 | `docs/{功能}.md` + `README.md` + 使用者文档对应页面 |
| 新增 / 修改模块或依赖组件 | `docs/开发规范.md` + `docs/{模块}.md` + `README.md` + 使用者文档对应页面 |
| 新增 / 修改前端包或路径别名 | 前端仓 `jffast-ui/docs/前端开发指南.md` + 前端仓 `README.md` |
| 修改技术栈版本 / 环境路径 / 仓库结构 / Git 策略 | 本文件 `AGENTS.md` |
| 修改 API 路径 / 分页 / 注解约定 | `docs/开发规范.md` |
| 影响 AI 判断的变更（模块、端口、技术栈） | 本文件 `AGENTS.md` |

> **对外可见即必改**：对外可见的功能、组件、API、接入协议变更，使用者文档为必改项，走「谁改动谁维护」。

### 2.3 文档组织规范

- 文档统一放 `docs/`，按模块或主题分目录，禁止散落在模块根或其他目录
- 每个业务模块维护 `README.md`，包含：模块说明、目录结构、文档清单
- 文档新增 / 移动 / 删除必须在对应模块 README 登记
- 引用路径一律使用**仓库根相对路径**；禁止 `../docs/xxx`、`./xxx`；写入前必须确认目标文件存在

### 2.4 文档生命周期

| 阶段 | 文档类型 | 必填内容 |
|------|----------|----------|
| 动手前 | 计划文档 | 需求背景、影响范围、变更清单、数据模型变更 |
| 实施中 / 后 | 开发文档 | 技术方案、变更文件清单、测试要点、注意事项 |
| 功能变更 | 同步文档 | 对应模块 README + 权威规范文档（如涉及编码约定） |

### 2.5 AI 行为规范（硬性）

1. **改完代码后必须检查文档**：每次修改代码后检查是否需要更新文档，未更新不得提交
2. **改文档前先登记 README**：新增 / 移动 / 删除文档文件后，同步更新对应模块 README 的文档清单
3. **引用路径校验**：写入文档引用前，确认目标文件存在且路径正确
4. **新增模块必建文档目录**：新增业务模块时在 `docs/` 下创建对应目录，并在模块 README 中登记

## 三、AI 规则文件（AGENTS.md）统一约定

- 所有项目**统一使用** `AGENTS.md` 作为 AI 规则文件，放各仓库根目录；不引入 `.cursorrules`、其他 IDE 私有规则文件等异构副本
- **统一结构**：概览 → 架构 → 开发规范 → 数据库 → 关键文件速查；内容具体真实（端口、包名、模块名），不写空话
- **锚定权威规范**：`AGENTS.md` 只做摘要 + 项目特有信息，编码细节引用权威规范文档，避免双份漂移
- **自足性**：别人单独拉取本仓库时，`AGENTS.md` 必须仍然完整可用，不得依赖仓库之外的文件
- **私有信息零残留（硬性）**：`AGENTS.md` 内不得出现任何私有信息（定义见文首）；实际值放 `LOCAL.md`，并在其中给出「哪些值本该写进 `AGENTS.md`」的对应说明，保证单独拉取时也知道去哪找
- **谁改动谁维护**：新增 / 修改 / 删除功能模块、端口、技术栈后，同步更新 `AGENTS.md`

## 四、提交规范

- 提交信息格式：`type(scope): description`，如 `feat(mybatis): add tenant interceptor`
- **提交信息不得含私有信息**（定义见文首）：身份类的姓名 / 邮箱 / 手机号 / 用户名，环境类的本机绝对路径、私有项目名、私有端口，一律不得出现
  - 需要说明工作内容时，用「模块 / 功能」措辞，不要用「我改了什么」这类第一人称表述
- 提交前自检：无密钥 / 密码 / 令牌等敏感值、**无私有信息**、无调试残留、文档已同步

## 五、通用红线

1. **不擅自扩大范围**：只做被要求的改动，不顺手重构、不引入未要求的抽象或配置项
2. **不硬编码敏感信息**：密钥 / 密码 / 令牌走环境变量或配置中心，禁止入库
3. **不泄露私有信息**（定义见文首）：写入 `AGENTS.md`、文档、提交信息、代码注释前，逐项核对身份类与环境类是否残留「具体值」；有值必迁 `LOCAL.md`
4. **不跳过验证**：声称「完成 / 已修复」前必须实际运行验证（编译 / 测试 / 页面操作），并给出证据
5. **不吞异常**：统一异常处理，禁止在业务代码里 try-catch 后静默忽略
6. **破坏性操作先确认**：删除文件、变更数据库、强制推送等操作先征得同意

<!-- END: 协作规范 -->

<!-- BEGIN: 后端规范 -->

# jffast 后端通用规范

## 一、复用优先（硬约束）

新增任何基类 / 工具类 / 公共方法 / 注解前，**必须先查下表**；表里已有就复用，没有才允许自建。
自建完成后**必须补进本表**，否则下一个人（或下一次 AI 会话）还会再造一遍。

### 1.1 可复用能力索引（增量维护）

> 完整表另见 `docs/开发规范.md` §18；此处为 AGENTS 速查。新增公共能力**当次补两处**。

| 类别 | 能力 | 全限定名 / 位置 | 何时用 |
|------|------|----------------|--------|
| 基类 | 实体基类 | `cn.jfspeed.common.data.base.BaseEntity` | 所有实体继承，勿重复定义审计字段/主键/逻辑删除 |
| 基类 | Service 基类 | `cn.jfspeed.common.data.base.BaseService` | 通用 CRUD / 分页 |
| 基类 | Controller 基类 | `cn.jfspeed.common.data.base.BaseController` | 统一响应、通用接口 |
| 响应 | 统一响应体 | `cn.jfspeed.common.web.domain.dto.R` | 所有接口返回值 `R.success` / `R.error` |
| 异常 | 业务异常 | `cn.jfspeed.common.core.exception.BusinessException` | 业务校验失败时抛出 |
| 异常 | 错误码枚举 | `IErrorCode` / `FrameworkErrorCode` / `AuthErrorCode` / `ApiErrorCode` | **禁止散落魔法值** |
| 工具 | 通用杂项 | `CommonUtil` / `ClassUtil` / `XmlParseUtil` / `MessageUtil` / `ULID` | `jffast-common/.../core/util/` |
| 工具 | ID 生成 | `cn.jfspeed.common.core.util.IdUtils`（**权威**；`generator.IdUtils` 为生成器内部） | 主键 / 业务单号 `ulid()` / `snowflakeShort()` |
| 工具 | 树结构 | `cn.jfspeed.common.core.util.TreeUtil` | 菜单、部门、分类 |
| 工具 | 分页转换 | `PageUtil` / `PageReq` / `PageRes` | `PageUtil.resultPageMp(page)` → `PageRes` |
| 工具 | 日期 | `DateUtils` | 格式化 / 解析 |
| 工具 | 校验 | `CheckUtils` | 抛业务异常的参数校验 |
| 工具 | Web | `ApiUtil` / `ResponseUtil` / `HttpUtils` / `UpDownUtil` / `IPUtil` | `jffast-common/.../web/util/` |
| 工具 | 安全 | `RSAUtil` / `AESUtil` / `EncryptionUtil` / `SignUtils` / `ApiParamSignUtil` / `RsaKeyGenerator` | 加解密与签名 |
| 工具 | 当前用户 | `cn.jfspeed.common.security.util.LoginUtil` | `getLoginUser()` / `getLoginUserId()` |
| 工具 | 脱敏 | `SensitiveMaskUtil` | 手机号、身份证等 |
| 工具 | DTO 基类 | `BaseQueryDTO` / `KvResponse` / `BaseEmptyEntity` | 查询 / 轻量响应 |
| 注解 | 操作日志 | `@SLog`（`jffast-starter-log`） | 写操作 |
| 注解 | 限流 | `@RateLimit`（`jffast-starter-security`） | 新增 / 敏感接口 |
| 注解 | 分布式锁 | `@DistributedLock` | 并发写同一资源 |
| 注解 | 权限校验 | `@SaCheckPermission` | 业务接口必加 |
| 注解 | 数据权限 | `@DataPermission` | 需要按部门/归属隔离（**不标注则不生效**） |
| 注解 | 敏感字段脱敏 | `@SensitiveMask` | 手机号、身份证等 |
| 注解 | 忽略基类字段 | `@IgnoreBaseField` | 实体映射排除基类列 |
| 扩展点 | 登录策略 SPI | `LoginPolicyProvider`（`.../security/spi/`） | 按插件替换登录策略 |
| 扩展点 | 定时任务 | `@JfFastJob`（`jffast-starter-job`） | 注解声明任务 |
| 扩展点 | OAuth2 客户端 | `jffast-starter-oauth2-client` | 授权码 / client_credentials |
| 配置 | 缓存 | `RedisUtil`（`jffast-starter-redis`） | 不要直接注入底层客户端乱用 |
| 配置 | 文件 / Excel | `jffast-starter-file` / `jffast-starter-excel` | 导入导出、附件 |

### 1.2 复用纪律

- 统一响应体之外**不返回裸对象**
- 异常统一抛业务异常，由全局异常处理器转换；**禁止 try-catch 吞异常后重包**
- 错误码一律用枚举，**禁止魔法值**（这是最容易失控的一处）
- 权限校验必须显式声明，禁止裸接口；确需匿名放行的，必须注释原因
- 分页参数名、响应结构全项目统一，新增接口不得自创

## 二、开发规范纲要

> 详细约定以 `docs/开发规范.md` 为准。本节只列高频、易错项。

### 2.1 分层与职责

```
controller/   仅做参数接收、权限注解、调用 service、返回统一响应；禁止写业务逻辑
service/      业务逻辑、事务边界
mapper/       数据访问
entity/       数据库映射
domain/       dto（入参）、vo（出参），禁止用 entity 直接接收/返回
```

### 2.2 参数校验

- 入参 DTO 用 `@Valid` + 约束注解，不在 service 里手写 if 判空
- 分层校验：格式类在 DTO，业务类在 service

### 2.3 事务边界

- 事务加在 service 层，不加在 controller
- 事务方法内**禁止**做远程调用、长耗时 IO
- 注意同类内部调用不走代理导致事务失效

### 2.4 数据库

- 字段 `snake_case`；主键与审计字段由基类/自动填充负责
- 逻辑删除字段统一命名并加对应注解
- 索引命名规范；少用多表 JOIN，复杂关联尽量在应用层完成
- schema 变更必须同步更新基线 SQL 脚本，不直接改生产库

### 2.5 接口约定

- CRUD 路径与 HTTP 方法全项目统一（分页 `/page`、详情 `/detail/{id}`、新增 `/create`、修改 `/update`、删除 `/{id}`）
- 分页入参 `pageNo`/`pageSize`、响应结构固定，见权威规范

## 三、测试形态

- **单元测试**：覆盖工具类、核心业务分支（含边界与异常分支）
- **集成测试**：覆盖接口层，至少验证鉴权、参数校验、异常转换三条链路
- 声称「已完成 / 已修复」前**必须实际运行**测试，并把结果贴出来

## 四、常见坑与已知限制

> 项目级坑位详见「项目信息」块「常见坑」；此处只保留与复用强相关的：

- **同名 Bean**：全包扫描下 base 与业务模块同时注册同名 Bean → 启动 `BeanDefinitionOverrideException`；新增 `@Bean` 先全局搜名称
- **`@DataPermission` 不标注完全不生效**（ThreadLocal 仅由注解填充），与配置开关无关
- **Sa-Token 为 `provided` 依赖**：无 Sa-Token 的服务勿把 Sa-Token 类型写进 catch 子句，用能力探测
- **SpEL 参数名是字符串契约**：`@DistributedLock(key = "'role:' + #req.id")` / `@Idempotent` 等注解里的 `#参数名` 不受编译器检查；改 Controller 方法参数名必须**全局同步**这些 SpEL，否则 key 静默求值为 null → 分布式锁失效，且无任何报错
- **新建文件先查重**：类名必须能自证语义、一个概念一个名字。曾发生 `SysMenuRequest`（菜单排序对 DTO）被同名的保存请求 DTO 覆盖，靠编译失败才兜住——新建类前先搜索确认不存在同名类
- **`sys_menu` 种子主键跨文件必须唯一**：可选模块脚本用 `INSERT IGNORE`，撞号时后导入的一方**整行静默丢弃**（表现为菜单永不出现且无报错）；段位分配与 `MenuSeedIdUniquenessTest` 门禁见《开发规范》§13

<!-- END: 后端规范 -->

<!-- BEGIN: 项目信息 -->

# jffast 项目信息

> 权威编码规范：`docs/开发规范.md`（出现不一致以该文档为准）

## 一、项目概览

- **定位**：基于 Spring Boot 4.1.1 的一站式后台管理框架（公开版，License **Apache 2.0**），「**可插拔 Starter**」设计。
- **License**：Apache 2.0
- **技术栈**：JDK 21 + Spring Boot 4.1.1 + MyBatis-Plus 3.5.16 + Sa-Token 1.46.0 + springdoc-openapi 3.1.1 + Flowable 8。认证为 **Sa-Token**（非 Spring Security）。
- **端口与环境**：后端 **7200**（context-path `/jffast-api`）、前端 Base **7201**；MySQL 8+（库 `jffast`）、Redis 7+。

### 1.1 顶层目录

```
jffast/                      ← 后端 Base 根（父 POM，packaging=pom，version 1.0.0）
├── jffast-bom/              版本管理（统一版本，业务模块禁止单独指定版本号）
├── jffast-common/           通用基础（BaseEntity/R/异常/工具）
├── jffast-starters/         Starter 聚合（12 个：mybatis/web/security/redis/log/file/excel/social/job/flowable/oauth2-client/demo-mode）
├── jffast-biz/              业务模块聚合（jffast-system / jffast-demo / jffast-flow / jffast-ai）
├── jffast-boot/             可运行应用（入口 cn.jfspeed.JfFastApplication）
├── sql/                     SQL 基线脚本
├── docs/                    功能文档（开发规范.md / flowable.md 等）
├── deploy/                  部署指南（README + compose + gen-rsa.sh + .env.example）+ Helm Chart
└── docker-compose.yml       只起 MySQL/Redis 的开发依赖；全套容器化在 deploy/
    .dockerignore            后端镜像构建上下文排除项
```

### 1.2 端口约定

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端（jffast-boot） | 7200 | context-path=`/jffast-api` |
| 接口文档（springdoc） | 7200 | `http://localhost:7200/jffast-api/swagger-ui.html` |
| 前端 Base（jffast-ui） | 7201 | dev server |
| MySQL | 3306 | 库名 `jffast` |
| Redis | 6379 | `spring.threads.virtual.enabled=true` |

### 1.3 默认账号

| 账号 | 密码 | 角色 |
|------|------|------|
| admin | test1234 | 超级管理员（`role_key=super_admin`；公开测试值，prod 下必须通过 `JFFAST_INIT_PASSWORD` 覆盖） |

> 由 `sql/system_init_data.sql` 初始化；`DataInitializer` 启动时 BCrypt 加密。

### 1.4 环境要求

| 项 | 要求 |
|----|------|
| JDK | **Java 21** |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Redis | 7.0+ |

编译（仓库根）：`mvn compile -DskipTests`  
本机绝对路径等实际值见不入库的 **`LOCAL.md`**。

## 二、架构与目录结构

### 2.1 模块结构

| 分类 | 模块 | 说明 |
|------|------|------|
| BOM | `jffast-bom` | 统一版本管理 |
| 公共 | `jffast-common` | `cn.jfspeed.common.*`：BaseEntity/R/异常/工具/登录 |
| Starter | `jffast-starter-mybatis` | 分页/乐观锁/自动填充/租户字段预留/数据权限 |
| Starter | `jffast-starter-web` | 全局异常/Jackson/springdoc/XSS/TraceId/Actuator + WebSocket（`/ws`，`WebSocketEventHandler` 业务扩展点） |
| Starter | `jffast-starter-security` | Sa-Token、@RateLimit、@DistributedLock、`LoginPolicyProvider` |
| Starter | `jffast-starter-redis` | RedisUtil 缓存 |
| Starter | `jffast-starter-log` | @SLog 操作日志 |
| Starter | `jffast-starter-file` | 本地/MinIO 文件存储 |
| Starter | `jffast-starter-excel` | FastExcel 导入导出 |
| Starter | `jffast-starter-social` | OAuth2 社交账号绑定（授权/回调/绑定，不签发登录 Token） |
| Starter | `jffast-starter-job` | Quartz 定时任务 + @JfFastJob |
| Starter | `jffast-starter-flowable` | Flowable 8 工作流引擎 |
| Starter | `jffast-starter-oauth2-client` | OAuth2 授权码客户端（默认 Redis 令牌缓存） |
| Starter | `jffast-starter-demo-mode` | 演示模式：DB 写短路 + 文件/任务副作用拦截（`jffast.demo-mode.enabled`，默认关闭） |
| 业务 | `jffast-system` | 系统管理核心（用户/角色/菜单/部门/字典/日志/任务/通知中心） |
| 业务 | `jffast-demo` | 示例模块（模板） |
| 业务 | `jffast-flow` | 工作流业务 |
| 业务 | `jffast-ai` | AI 知识库存储管理（知识库/文档/对话，仅存储） |
| 启动 | `jffast-boot` | 可运行应用 |

### 2.2 依赖关系

```
jffast (pom)
├── jffast-bom / jffast-common
├── jffast-starters (pom)   ← 12 个 Starter
├── jffast-biz (pom)        ← system / demo / flow / ai
└── jffast-boot             ← 聚合业务 + file/excel/social + MySQL 驱动
```

### 2.3 新增模块步骤（三处注册）

1. `jffast-biz/pom.xml`：`<modules>` 增加 `<module>jffast-{业务名}</module>`
2. `jffast-bom/pom.xml`：`<dependencyManagement>` 增加该模块 dependency + `${project.version}`
3. `jffast-boot/pom.xml`：增加依赖

### 2.4 核心配置要点

- **`application.yml`**：端口 7200、context-path、`jffast.mybatis.tenant.enabled=false`（默认关）、`data-scope.enabled=true`、虚拟线程、Quartz JDBC、sa-token
- **`application-dev.yml`**：数据源 / Redis 经环境变量；RSA `jffast.rsa.*`（`RsaKeyGenerator` 生成）
- 安全：密码前端 RSA → 后端 BCrypt（强度因子 12，见 `EncryptionUtil#BCRYPT_LOG_ROUNDS`）；XSS 过滤覆盖 form/query/header **以及 JSON 请求体**（`WebProperties#jsonClean` 默认 true，`bpmnXml` / `contentTemplate` 等代码类字段经 `jsonSkipFields` 豁免）

## 三、环境要求（汇总）

见 §1.4。本机专属配置（绝对路径、本地账号）**不要写在这里**，放不入库的 `LOCAL.md`。

## 四、开发规范索引

| 主题 | 位置 |
|------|------|
| 编码规范（权威） | `docs/开发规范.md` |
| 前端指南 | 前端仓 `jffast-ui/docs/前端开发指南.md` |
| 工作流 | `docs/flowable.md` |
| 限流 | `docs/api-rate-limiting.md` |
| 演示模式 | `docs/demo-mode.md` |
| 可复用能力索引 | 本文件「后端规范」块 + `docs/开发规范.md` §18 |

### 4.1 AI 任务路由表（最短正确路径）

> 原则：**优先用生成器和门禁，而不是背诵规范**。本表给出常见任务的唯一正确入口。
>
> **分层说明（公开仓）**：本仓是可**单独拉取、自足**的公开层。它的「完成定义」只由本仓门禁构成——后端 `mvn test` 全绿；本仓不得引用仓库之外的路径、脚本或机制（见协作规范 §三「自足性」）。跨项目聚合校验不属于本仓。

| 任务 | 正确做法 | 禁止 / 陷阱 |
|------|---------|------------|
| 新增 CRUD 模块 | 用生成器 CLI（不启动应用）：`mvn -q -pl jffast-common -am compile exec:java -Dexec.mainClass=cn.jfspeed.common.core.util.CodeGeneratorCLI -Dexec.args="--url=jdbc:mysql://127.0.0.1:3306/jffast --user=root --password=xxx --table=sys_xxx --package=yyy"`；树表加 `--tree --parent-field=parent_id --name-field=name` | 手写整套样板代码；在子目录运行（写入位置按运行目录推断） |
| Controller 接收前端写入 | `@RequestBody @Valid XxxRequest` + `BeanUtil.copyProperties(req, Xxx.class)`，模板见 `DemoExampleController` | `@RequestBody XxxEntity`（会被 `ArchitectureConventionTest` 拦下） |
| 改 Controller 方法签名 | **同步检查方法上所有注解的 SpEL**（`@DistributedLock` / `@Idempotent` 的 `#参数名`） | 只改签名——SpEL 不报编译错，只会静默失效 |
| 新建类 / DTO / 工具 | 先搜索确认同名类不存在，类名自证语义 | 先建文件后查重 |
| 加权限 / 限流 / 操作日志 | `@SaCheckPermission` + `@RateLimit("default")` + `@SLog` 三件套 | 裸接口（权限会被 `EndpointPermissionAnnotationTest` 拦） |
| 规范存疑 / 自查 | 跑 `jffast-boot` 的 `ArchitectureConventionTest` + `EndpointPermissionAnnotationTest`，红了按提示信息改 | 只读文档不验证 |
| 生成代码在 JAR/容器里 | 改用 `POST /sys/codegen/download-zip` 打包下载 | 依赖 `save-local`（JAR 下被拒绝） |
| 完成定义（改框架源码 common/starters/biz 收尾） | 唯一门禁：`mvn test` 全绿——已含 `ArchitectureConventionTest` / `EndpointPermissionAnnotationTest` / 反射契约等规范测试；改了公共约定再同步 `docs/开发规范.md` / `CHANGELOG.md` 与相关模块 README | 声称「已完成」前必须实际运行测试并贴结果；**不**新增本仓之外的门禁命令 |

## 五、关键文件速查

| 文件 | 位置 | 说明 |
|------|------|------|
| 应用入口 | `jffast-boot/src/main/java/cn/jfspeed/JfFastApplication.java` | mainClass |
| 主配置 | `jffast-boot/src/main/resources/application.yml` | 端口/开关/Quartz/sa-token |
| 开发配置 | `jffast-boot/src/main/resources/application-dev.yml` | 数据源/RSA/环境变量 |
| 父 POM / BOM | `pom.xml` / `jffast-bom/pom.xml` | 聚合与版本 |
| 启动装配 | `jffast-boot/.../config/DataInitializer.java` | admin 密码初始化 |
| 示例模板 | `jffast-biz/jffast-demo/.../` | 新模块参考 |
| 编码规范（权威） | `docs/开发规范.md` | 后端全部规范 |
| 前端指南 | 前端仓 `jffast-ui/docs/前端开发指南.md` | 前端交互规范 |
| 部署 | `deploy/README.md` + `deploy/helm/` | 后端镜像 `jffast-boot`（`jffast-boot/Dockerfile`）；前端镜像在 jffast-ui 仓库，两者独立编排 |

## 六、常见坑与已知限制（源码核验过）

> happy-path 见权威文档；此处为「照文档做会踩坑」。

### 6.1 数据权限只在 `@DataPermission` 标注时才触发

- `DataScopeInnerInterceptor` 首行判断 `DataScopeContext.get()` 为空即 return；该 ThreadLocal **仅由注解填充**。
- 不标注解 → 完全不生效（与 `data-scope.enabled=true` 无关）。
- 无 `dept_id` 列的表不要照抄 `exist=false` 写法；有列且要隔离时用 `@DataPermission` + `@IgnoreBaseField(deptId=true)`。

### 6.2 `jffast-starter-file` 已知限制

- 配置 key 与自动装配不对齐 → 配了可能不生效；路径强制时间戳子目录；`delete()` 只删单文件。
- 需要确定性落盘路径时自建 `XxxFileService`，接口语义照抄 `FileStorageService`。

### 6.3 JSON 列处理

- 实体字段 `@TableField(typeHandler = JacksonTypeHandler.class)` 且类上 `@TableName(autoResultMap = true)`。
- 需频繁检索优先子表 + `search_text`，少塞单 JSON 字段。

### 6.4 菜单来自数据库（前端路由）

- `VITE_USE_STATIC_ROUTES=false` 时菜单由 `sys_menu` 驱动。
- **只加 `router/modules/*.ts`、不插 `sys_menu` → 菜单不显示**。

### 6.5 其他速查

- Starter Bean 注册统一 `@Bean`，禁止 `@Component`/`@Service` 双重注册
- 同名 Bean / 提供 `provided` 的 Sa-Token / 未 clean 打包残留旧 class：见协作与历史验收记录

### 6.6 异步任务的数据权限与审计上下文

- **worker 没有登录态**：提交线程必须先用 `DataPermissionSqlBuilder.resolve(目标类.class, "方法名", 参数类型...)` 按目标方法的 `@DataPermission` 元数据算出 SQL，再连同 `LoginUtil.getLoginUserId()` 包成 `SysTaskContext(operatorId, dataScopeSql)` 传给 `SysTaskRunner.submit`。`resolve` 在方法不存在/缺注解时直接抛错，必须**在创建任务之前**调用——失败即中止，不产生孤儿任务。
- `SysTaskRunner` 在 worker 内恢复 `DataScopeContext` 与 `AuditContext`，并在 `finally` 还原调用线程；队列满走 `CallerRunsPolicy` 时任务在请求线程执行，不还原会污染请求上下文。`jffast.system.task.queue-capacity` 必须 > 0，否则启动失败。
- `sys_task` 按 `create_by` 隔离（超管全量）：分页/详情/下载/删除统一走 `getOwnedTask`；详情把 `filePath` 置空；下载/删除统一走 `resolveTaskFile`，只允许本地存储根下的 `task` 子目录，越界路径只记日志、不读也不删。
- **任务结果文件是本地单实例存储**：默认落在 `jffast.file.storage.local.path` 的 `task` 子目录。多实例部署必须挂共享目录或改对象存储，否则 A 实例生成的任务在 B 实例下载会返回「文件不存在或已清理」。

### 6.7 角色写入与授权变更（会话失效）

- 角色新增/更新只走 `SysRoleService.createRole` / `updateRole`（`saveRole` 已移除）。有效 `roleKey`/`dataScope` 由 `resolveEffectiveRole` 一次算出并写回实体，服务层不再补默认值；`createRole` 还要求实体 ID 非空（fail-closed）。
- 绑定集合语义：`menuIds`/`deptIds` 的 `null` = 不修改、`[]` = 清空、非空 = 替换；`createRole` 时 `null`/`[]` 等价无绑定；`insertRoleDept(null)` 是 no-op + warn；重复 `deptId` 先去重（否则撞 `(role_id, dept_id)` 主键）。
- 授权字段（`roleKey` / `dataScope` / 自定义部门绑定集合）变化时，在**事务内**发布 `RoleAuthorizationChangedEvent`；`AuthorizationChangedListener` 以 `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)` **同步**撤销绑定用户会话（禁止加 `@Async`）。事务回滚不踢人；创建角色、仅改菜单绑定、仅改 `isOpen` 都不踢人。用户角色/部门变化走 `UserAuthorizationChangedEvent`，路径相同。

## 七、待填占位符

当前无待填占位符。（早期 `{{文档站路径}}` 占位已收敛：使用者文档位于独立文档站，本仓不再引用具体路径。）

<!-- END: 项目信息 -->
