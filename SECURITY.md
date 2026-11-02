# 安全策略

## 支持版本

| 版本 | 支持状态 |
|------|----------|
| 1.0.x | ✅ 当前支持 |

> 当前首发版本为 `1.0.0`（全部 pom / Helm Chart 同版本）。仅最新 minor 分支获得安全修复，请尽量升级到最新版本。

## 报告漏洞

请**不要**通过公开 GitHub Issue 报告安全漏洞。

请使用 [GitHub Security Advisories](https://github.com/jfspeed/jffast/security/advisories/new) 提交私密报告（需在仓库 Settings → Security 打开 "Private vulnerability reporting"）。本仓库不设邮箱渠道——写死的邮件地址一旦域名失效，上报就会石沉大海，而 GitHub 私密报告有送达记录。

报告请包含：

1. 漏洞描述与影响范围
2. 复现步骤 / PoC
3. 受影响版本
4. 建议修复方案（如有）

我们将在 **7 个工作日内**确认收到，并在修复后公开致谢（除非你要求匿名）。

## 安全基线（部署检查）

- 生产环境关闭接口文档：`jffast.swagger.enabled=false`
- 确认管理员初始口令：默认 `test1234` 为随仓库公开的测试值，**仅限本地体验**；prod profile 下 `JFFAST_INIT_PASSWORD` 必填（缺失启动失败），且上线后请立即登录改密
- 前端 RSA 公钥与后端私钥必须成对替换，禁止使用仓库示例密钥
- CORS 仅放行可信来源：`jffast.web.cors.allowed-origin-patterns`
- 多实例部署时开启 Quartz 集群（见 `deploy/README.md`）
- 上传目录使用独立路径并限制类型/大小

## 已知取舍（有意保留，不是缺陷）

下面三条在「安全性 vs 开箱即用」之间选择了后者。**部署方必须知道它们的边界**，请勿再次当成漏洞提交。

### 1. `/upload/**` 免登录可读

文件存储根同时决定上传根目录与 `/upload/**` 的静态映射，且该路径在 Sa-Token 免登录白名单里。

- **为什么保留**：头像、附件这类图片要能被 `<img src="...">` 直接引用。`<img>` 不会携带 Authorization 头，
  一旦要求鉴权，页面上的头像与图片附件会全部失效，只能改造成签名 URL / blob 下载，代价远超收益。
- **因此必须记住**：存储根内的**每一个文件都视同对外公开**。URL 形如「日期目录 + 随机文件名」不可枚举，
  但**一旦泄露就是永久可读**，且没有任何访问审计。
- **不要往里放敏感文件**：服务端生成的产物写在存储根之外——这正是 `jffast.system.task.file-dir`
  默认指向 `./task-files`（而非 `./upload/task`）的原因，配成根内子目录会直接启动失败。
- 若业务需要把附件变成私有：**不要**在 `/upload/**` 上加鉴权（会连带打碎 `<img>` 引用），
  请改用 `SysFileController#download/{id}`（已按 `create_by` 做归属校验）作为唯一读取通道。

### 2. 默认 profile 是 `dev`

新人 clone 后应当能零配置跑起来，因此 `spring.profiles.default=dev`；配套的 `test1234` 是随仓库公开的
统一**测试填充值**（prod profile 下 `JFFAST_INIT_PASSWORD` 必填，缺失即启动失败）。

弱口令本身不是风险，**把它用在公网可达的端点上才是**。因此 `DevProfileGuard` 做了一道兜底：
当 dev/test profile + 公开测试口令 + **远程数据源**三者同时成立时**直接拒绝启动**
（`demo` profile 的演示站只告警不拦截，因为它是显式对外展示形态）。此外 `application-prod.yml`
把接口文档与 Sa-Token 会话日志一并关闭，RSA 密钥与初始口令全部改为必填。

**部署到公网时**：显式 `--spring.profiles.active=prod`，或至少替换数据源口令与初始口令。

### 3. 字典基础读接口「登录即可」，不要求 `system:dict:view`

`GET /sys/dict/getByTypeCode?dictCode=xxx` 只要求登录态，**不要求**字典管理权限码。

- **为什么保留**：业务字典是全局只读数据，前端通用下拉框 / 单选组 / 状态标签几乎每个登录用户都会用到。
  此前该接口挂 `system:dict:view`（或超管二选一），意味着除超管外的账号一律 403 —— 普通用户打开任何含下拉框的页面都被拦，
  与「字典是只读公共数据」的定位冲突。它与字典管理页的 `GET /sys/dict/page` 是两回事：后者带分页、检索与管理字段，仍受权限码保护。
- **边界**：出参**只含 5 列**（`id` / `name` / `code` / `value` / `sort`），
  字典项的录入人、租户等审计列**不随本接口下发**（见 `SysDictService#getListByDictCode` 的 `select(...)`）。
- **多租户隔离**：Pro 形态下按 `tenant_id` 过滤由租户拦截器完成，缓存键也含租户维度（`SysDictService#cacheKeyOf`），
  不会把一个租户的字典读给另一个租户。
- **登记依据**：该端点按「接口必须显式权限注解或白名单登记」的约定，在 `EndpointPermissionAnnotationTest` 的白名单中带理由登记；
  若部署方对字典读取有更严要求，请在前端改用带权限码的 `/sys/dict/page`，或自行加网关侧管控。

## 依赖版本管理

传递依赖即使本仓不用其 API，也会进制品、也会被扫描器按字面命中。本仓的做法是**在 BOM 里钉住版本**，
而不是等上游传递链自己带对——BOM 的 `dependencyManagement` 优先级高于传递依赖，因此改一处即可全仓生效。

以 `org.bouncycastle:bcprov-jdk18on` 为例：**本项目源码零引用**，真正的引入者是 `commons-compress`
（经 POI）这条传递依赖链。它曾报出 4 条告警（GOST 28147 CTR 密钥流复用、LDAP 注入、X.509
Name Constraints 绕过、ASN.1 嵌套深度守卫重置）。这不是「取舍」，是版本管理问题——
本仓已在 `jffast-bom` 把该构件钉在 **1.86**（1.x 线当前最新），4 条全部消除：

| 构件 | 钉住的版本 | 为什么由本仓钉 |
|------|-----------|---------------|
| `org.bouncycastle:bcprov-jdk18on` | `1.86` | 传递依赖（经 POI / commons-compress），本仓源码零引用但仍进制品。1.86 修复了 GOST 28147 CTR 密钥流复用（CRITICAL）、LDAP 注入、X.509 Name Constraints 绕过（CRITICAL）、ASN.1 嵌套深度守卫重置，公开漏洞库对该版本零命中 |

- `jffast-biz/jffast-system` 里那条 `bcprov-jdk18on` 声明**不带版本号**，版本统一由 `jffast-bom` 管理——
  **改版本只改 BOM 一处**，避免两处版本号漂移。
- **升级到 2.x 的代价**：2.x 需要连带改 `bcpkix` / `bcutil` 且属大版本迁移。本仓未使用 2.x，故不涉及。
- 后续上游再发版时，直接改 `jffast-bom` 的版本号即可，1.x 线 API 兼容。

## 已加固项基线

下列默认值已刻意收紧，改动前请先读这里的理由：

| 配置 | 当前值 | 为什么 |
|------|--------|--------|
| `flowable.process.enable-safe-xml` | `true` | 关闭 BPMN 的 DOCTYPE / 外部实体（XXE）。本仓开放 `POST /flow/process/deploy` 接收任意 BPMN XML。注意键名是 `enable-safe-xml`（对应字段 `enableSafeXml`），写成 `enable-safe-bpmn-xml` 是无效键，会被宽松绑定静默忽略 |
| `jffast.file.download-max-bytes` | `52428800`（50MB） | `LocalFileStorageService#downloadFile` 走 `Files.readAllBytes` 全量入堆；`0`（不限制）时并发下载若干大文件即 OOM |
| `sa-token.active-timeout` | `7200` | 原 `-1`（永不过期）会让被踢出 / 已吊销的 token 一直可用 |
| `sa-token.is-log` | `false` | 会话日志会打印账号信息；仅在排查会话问题时临时打开 |
| BCrypt 强度因子 | `12` | OWASP 当前建议下限。强度写在哈希串里，调整该值**不影响存量口令校验**（见 `EncryptionUtil#BCRYPT_LOG_ROUNDS`） |

> Flowable ≤ 8.0.0 的布局解析器存在 XXE（CVE-2026-91197），截至 2026-10 上游仍无修复版本。
> 本仓**不调用** `GetDeploymentProcessDiagramLayoutCmd`（jar 内唯一触发点），因此该攻击链在当前代码不可达；
> 部署/解析链路已由 `enable-safe-xml` 兜住。上游发布修复版本后请直接升级。
