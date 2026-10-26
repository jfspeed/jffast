# 发布信息 · jffast（未发布，首发将定版 v1.0.0）

> 本文件是**发布工件与升级操作口径**（发什么、怎么升、升完怎么验）。功能亮点的叙述见 [docs/release-highlights.md](docs/release-highlights.md)；逐条变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 1 版本标识

| 项 | 值 |
|----|----|
| 组件 | `jffast`（疾风后端 Base：通用基础 + Starter 体系 + system/flow/demo/ai 业务模块 + 可运行 `jffast-boot`） |
| 本包版本 | `1.0.0-SNAPSHOT`（开发快照，**尚未发布**） |
| 上一版本 | 无（尚未正式对外发布） |
| 发布轮次 | 首发（V1） |
| 收录范围 | 当前 master 全部提交（首发一次性收录） |
| 许可证 | Apache-2.0 |
| 配套前端 | `jffast-ui`（与本包同步开发、同轮发布，见其仓库发布文件） |
| 坐标 | `cn.jfspeed:jffast-*`，BOM：`cn.jfspeed:jffast-bom` |

## 2 版本口径说明

- **轮次（V1/V2…）** 是文档口径的功能发布序号，用于说明「本包相对上一轮发了什么」。
- **语义版本**：当前开发版本保持 `1.0.0-SNAPSHOT`；正式发布时由发布指令统一定版（**首发 = v1.0.0**）并清理提交记录，在此之前不固化版本号、不打 tag。
- **现状差异**：全部 pom 保持 `1.0.0-SNAPSHOT`；发布前按发布指令统一定版。
- Helm Chart（`deploy/helm/jffast`）的 `appVersion` / 镜像 `tag` 已对齐为 `1.0.0-SNAPSHOT`；正式发布时随 pom 一起定版为 `v1.0.0`。

## 3 组件与版本矩阵

| 层次 | 组件 | 版本 |
|------|------|------|
| 运行时 | JDK | 21（启用虚拟线程） |
| 框架 | Spring Boot | 4.1.1 |
| 持久层 | MyBatis-Plus | 3.5.16 |
| 鉴权 | Sa-Token | 1.46.0 |
| 接口文档 | springdoc-openapi | 3.1.1 |
| 工作流 | Flowable | 8.0.0 |
| 工具 | Hutool / Lombok | 5.8.43 / 1.18.46 |

模块清单（12 个 Starter + 4 个业务模块 + 3 个基础模块）：

- **基础**：`jffast-bom`、`jffast-common`、`jffast-boot`
- **Starter**：`web`、`security`、`mybatis`、`redis`、`log`、`file`、`excel`、`job`、`flowable`、`social`、`oauth2-client`、**`demo-mode`（本包新增）**
- **业务**：`jffast-system`、`jffast-flow`、`jffast-demo`、**`jffast-ai`（本包首次纳入文档口径）**

## 4 运行前提

| 项 | 默认值 | 环境变量 |
|----|--------|----------|
| 服务端口 / context-path | `7200` / `/jffast-api` | — |
| MySQL | `127.0.0.1:3306`，库 `jffast` | `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_DB` |
| Redis | `6379`，database `0` | `REDIS_PORT` / `REDIS_DATABASE` |
| 登录密码 RSA | dev/test 启动时自动生成一次性密钥对 | `RSA_PUBLIC_KEY` / `RSA_PRIVATE_KEY`（**生产必须成对显式注入**） |
| 初始管理员口令 | 默认 `test1234`（公开测试值，仅本地体验） | prod 必填 `JFFAST_INIT_PASSWORD`（缺失启动失败） |

## 5 升级步骤

1. **先处理破坏性重命名（自 jpfast 旧版迁移必做）**：包根 `io.github.jpfast` → `cn.jfspeed`，模块/构件 `jpfast-*` → `jffast-*`，API 前缀 `/jpfast-api` → `/jffast-api`，环境变量 `JPFAST_*` → `JFFAST_*`，yml 根键 `jpfast:` → `jffast:`，类前缀 `JpFast*` → `JfFast*`。接入方需全局替换 import、依赖坐标、配置前缀与网关转发路径。默认数据库名 `jp_fast` → `jffast`（jdbc 默认值、docker-compose、Helm values 已同步；存量库可继续用旧名，改 `MYSQL_DB` 即可）。
2. 依赖版本改用 `jffast-bom`（`import` 到 `dependencyManagement`），不要逐个指定版本。
3. SQL 按 `README.md`「初始化数据库」的顺序执行——**该处是唯一权威清单，此处不再复制**（复制即产生第二事实源，此前就漏过 `sys_post` / `sys_task` / `sys_file` 三份，而这三份是岗位、异步任务、文件管理菜单的来源，漏导则菜单永不出现）。表结构变更一律直接内建在建表语句里（项目尚未发布，没有需要升级的存量库）。
4. 定时任务改造后（本包）：任务归属改为注解声明式，`sys_job` 增 `service_name` 列——升级后需为存量任务补归属服务，否则按本服务处理（详见 `docs/开发规范.md`）。
5. WebSocket 接入方：握手 token 支持子协议传递（保留 query 兜底）；业务回调改用 `WebSocketEventHandler` SPI 注册，不再改框架代码。
6. 部署工件：`jffast-boot/Dockerfile`（容器镜像，仓库根 `docker-compose.yml` 只起 MySQL/Redis 开发依赖）、`deploy/docker-compose.yml`、`deploy/helm/`；本包修补了 profile 透传与 RSA 注入缺口（见 `deploy/README.md`）。

## 6 发布前待办与校验清单

- [ ] 发布时执行：`pom.xml` 及各子模块版本统一定版（首发 `v1.0.0`，当前保持 `1.0.0-SNAPSHOT`）
- [ ] 发布时执行：`CHANGELOG.md` 的 `[Unreleased]` 段落定版并标注日期
- [ ] `mvn clean install -DskipTests=false` 全绿 + JaCoCo 门禁通过（本机已验证全绿；**CI 尚未实跑**——本仓库尚未配置远程，push 后由 CI 实跑再据实勾选）
- [ ] 冒烟：登录（RSA + 验证码 `/sys/needCaptcha`）、CRUD、代码生成器、定时任务、工作流审批人解析、WebSocket 推送（本机已跑通 19 项 HTTP 冒烟；浏览器端 E2E 未跑）
- [ ] 发布时执行：打 tag（首发 `v1.0.0`）并推送（本仓库当前无任何 tag，版本追溯依赖 CHANGELOG）
- [ ] 文档站 / README 的「相关项目」「版本」表述与本包一致（独立文档站尚未上线；仓库内已不引用 `jffast.cc`，站外链接一律指向 GitHub 仓库与 `docs/`）

## 7 已知限制

- 仓库当前无任何 tag（尚未正式发布）；首发发布时起建议每轮发布即打 tag，便于版本追溯。
- 仓库**不存放任何密钥字面量**：dev/test 启动时由 `RsaKeyInitializer` 生成一次性密钥对，前端经 `GET /sys/rsaPublicKey` 动态取公钥；生产必须自行生成密钥对并成对注入 `RSA_PRIVATE_KEY` / `RSA_PUBLIC_KEY`（前端 `VITE_RSA_PUBLIC_KEY`）。发布构建若注入的公钥为空或等于仓库内的示例公钥，`vite.config.ts` 直接 fail-fast。
- 多租户相关表结构与开关已预留，Base 提供承载点与示例数据。
