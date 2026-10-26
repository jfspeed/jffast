# 更新日志

本项目遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 格式，版本号遵循[语义化版本](https://semver.org/lang/zh-CN/)。

> **版本口径说明**：本项目**尚未正式发布**，当前所有 pom 保持 `1.0.0-SNAPSHOT`（见 [release-info.md](release-info.md)）。
> 下文标题中的版本号与日期是**开发里程碑**（能力批次）标记，**不是已发布的 tag**；正式发布时将由发布指令统一定版（**首发 = v1.0.0**）。

## [Unreleased]

### 新增

- 第三方署名与许可证随包：新增根级 `NOTICE`（第三方组件逐项署名，含 MySQL Connector/J 的 GPLv2+UFO 说明与非 MySQL 驱动的再分发口径）；构建期由 `maven-resources-plugin` 把仓库根的 `LICENSE` / `NOTICE` 复制进 fat jar 的 `META-INF/`（Apache-2.0 §4(a)(d) 要求二进制分发随附许可证副本）
- 用户部门数据范围：非超管创建用户必须显式指定范围内 `deptId`（缺失或越范围 → 业务码 403，`roleList`/`deptList` 一律拒绝），创建成功后自动补写一条 `sys_user_dept` 绑定（新增 `SysDeptScopeQuery`），保证新账号在创建者数据范围内可见
- 用户导入归属部门：导入模板新增第 6 列「归属部门」（列样式为文本、表头带填写说明，可填部门名称或部门 ID）；非超管必填且须落在导入者可见部门内（名称精确匹配、重名要求改填 ID、越权/不存在按行失败），超管留空表示不设归属部门；成功行同时写 `sys_user.dept_id` 并补写恰好 1 条 `sys_user_dept` 绑定。导入专用模型 `SysUserImportExcel` 与导出模型 `SysUserExcel` 拆分，导出仍为 5 列
- 用户导入上下文快照：`SysUserImportContextFactory` 在请求线程固化「是否超管 + 可见部门快照 + 重名部门集合」，异步导入 worker 全程复用快照，不在 worker 内读取登录态或数据权限
- 用户写侧不变量（服务端强制，HTTP 200 + 业务码 403）：清空原有非空部门绑定（INV-1，超管编辑他人同样适用）；归属部门不在本次提交或既有绑定集合内（INV-2）；创建时显式绑定集合不含 `deptId`（INV-3）。前端 `user-edit-rules` 校验保留为第一道提示
- 数据范围 fail-closed：`dataScope=none` 生成 `1 = 0`，受限用户的数据权限不再退化为全量；配套 `SysUserDataScopeTest` / `DataPermissionSqlBuilderMetadataTest`
- 用户管理授权拒绝统一为 HTTP 200 + 业务码 403（`SysUserController` 9 处）；配套 `SysUserControllerAuthorizationCodeTest`
- 流程数据权限 `FlowAccessService`：签收/办理校验办理人或候选人，转办仅办理人，任务详情/审批历史校验办理人/候选人/发起人，流程实例读取校验发起人/参与人，取消校验发起人或超管；非超管查询运行中实例时强制按本人发起人过滤
- 社交登录 state 改为 HMAC-SHA256 签名 + 10 分钟过期；回调额外签发 5 分钟一次性 `bindTicket`，绑定接口不再信任客户端提交的社交用户资料（密钥取 `jffast.social.state-secret`，未配置时由各 provider 的 `client-secret` 派生）
- 权限缓存：`SysMenuService#listAllBtnPerm`（超管权限全集）与 `SysRoleMenuService#listAllBtnPerm`（按角色）改为 3600s TTL 缓存，角色-菜单/菜单权限变更统一失效（含历史无 TTL 哈希条目），超管鉴权不再每次全表扫描 `sys_menu`
- 文件上传统一走 `FileUploadValidator`（大小 + MIME 白名单），本地 / MinIO / OSS / COS 四种存储共用；MinIO 下载/删除的对象键解析支持 endpoint 前缀、查询串与 fragment，并拒绝空键与 `..` 路径
- 新增回归测试：`FlowAccessServiceTest`、`AiConversationServiceTest`、`SysRoleMenuServiceCacheTest`、`MinIOFileStorageServiceObjectKeyTest`、`SignedStateUtilTest`、`SysUserControllerUpdatePasswordTest` 等
- 仪表盘内部端点 `/internal/dashboard/{stats,logTrend,recentLogs}`（无 `@SaCheckPermission`，供服务间调用）：Sa-Token 注解鉴权要求登录会话，而「已验签内部调用」（分布式形态按共享密钥验签）按设计没有会话，带注解的目标端点会让内部 Feign 调用恒 401。**路径刻意放在 `/internal/**`（网关不路由，浏览器不可达）**——早期版本放在 `/sys/dashboard/inner/*`，但网关会为所有已登录浏览器请求签名，下游无法凭签名区分浏览器与 Feign，内部端点实际对任意登录用户开放。控制器内改为显式判权：已验签内部调用放行，其余直连须超管或 `system:log:view`；未登录直连仍被 `SaInterceptor` 拒绝
- per-IP 限流与可信代理：`jffast.security.login-ip-limit` / `captcha-ip-limit` / `captcha-check-ip-limit` 分别对 `POST /sys/login`、`GET /sys/getCode`、`GET /sys/needCaptcha` 按客户端 IP 分桶限流（与 `@RateLimit` 全局桶叠加：全局兜总量，本限流按来源分桶；阈值/文案可配，Redis 不可用 fail-closed）。真实客户端 IP 仅在 TCP 对端命中 `jffast.security.trusted-proxies` 时按 `X-Forwarded-For` 逐跳回溯，否则不读转发头（可经环境变量 `JFFAST_SECURITY_TRUSTED_PROXIES` 注入）。详见 `docs/api-rate-limiting.md`
- Spring Boot 4.1 升级：`4.1.1`（JDK 21 不变）+ Jackson 3（`tools.jackson` 包名）；Flowable `7.1.0 → 8.0.0`；Knife4j 官方无 Boot 4 版本，改用 springdoc-openapi `3.1.1` 原生 UI（入口 `/swagger-ui.html`，`/doc.html` 暂 404，将来回切只需换一个 starter 坐标）；Sa-Token `1.45.0 → 1.46.0`（boot4 starter + `sa-token-jackson3`）；MyBatis-Plus 刻意停 `3.5.16`（boot4 starter 已提供）；`resilience4j-spring-boot4 2.4.0`；`starter-aop → starter-aspectj`
- Sa-Token session 白名单：新增 `jffast.security.sa-json-allow-types`（`SysUser` / `SysRole` / `SysDept`），经 `BeanFactoryPostProcessor` 在 Sa-Token 初始化前注册；回归测试 `SaJsonSessionWhitelistTest`（白名单覆盖 session 图谱 + 真实往返序列化）
- Flowable 8 的 process starter 默认带入 event-registry，`flowable.eventregistry.enabled=false` 关闭（base / pro-boot / cloud-flow 同口径）
- 部署：`deploy/gen-rsa.sh` 一次生成 RSA 密钥对并同时输出后端两行与前端一行 —— 此前前后端各配各的，不成对时服务能正常启动、只有登录必失败，很难定位；配套 `deploy/.env.example` 列出后端栈变量清单
- 部署文档新增「独立部署」章节：两个跨镜像契约（API 前缀 / RSA 成对）、构建期与运行时变量的区别、K8s 多副本注意事项
- 新增规范测试 `MenuSeedIdUniquenessTest`：扫描 `sql/*.sql` 中 `sys_menu` 的显式主键，跨文件重复即失败（解析失效时按样本量下限报错，避免「0 冲突」假绿）
- 异步任务上下文快照：导入/导出提交时在请求线程快照操作者与数据权限 SQL（`SysTaskContext`），worker 恢复并在结束后还原；线程池队列容量可配 `jffast.system.task.queue-capacity`（默认 100，必须 > 0，≤ 0 启动失败）
- 用户与角色写侧授权：非超管不得分配角色/部门、不得修改自己的角色/部门、不得移动他人部门；角色新增/更新改用 `createRole`/`updateRole`，有效 `roleKey`/`dataScope` 一次计算并写回，服务层不再提供默认值
- 授权变更会话失效：角色授权字段（`roleKey`/`dataScope`/自定义部门绑定集合）或用户角色/部门变化时，事务提交后同步撤销绑定用户会话（`RoleAuthorizationChangedEvent`/`UserAuthorizationChangedEvent`，`AFTER_COMMIT`，禁止 `@Async`）；角色创建不发布事件
- 异步任务归属隔离：分页/详情/下载/删除按 `create_by` 隔离（超管全量），详情不返回服务器路径，下载仅允许任务目录内文件
- Actuator 暴露分层：默认与生产仅 `health`/`info`，`dev` 额外暴露 `metrics`/`prometheus`；`/actuator/health/**` 免登录
- 文件下载上限 `jffast.file.download-max-bytes`（默认 0 表示不限制）

### 修复

- 社交登录开启即 401：`/social/authorize`（跳第三方）与 `/social/callback`（第三方回跳）发生在**登录之前**，浏览器此时没有登录态；而这两个路径既不在 `SaTokenConfig.PUBLIC_PATHS` 也不在 `jffast.security.auth-excludes` ⇒ `StpUtil.checkLogin()` 直接抛 401 ⇒ **整条社交登录链完全不可用**（实测 `GET /social/authorize` → HTTP 401，对照组 `/sys/rsaPublicKey` 200）。base 与 pro 的 `application.yml` 均补上这两个精确路径（社交端点由 `@ConditionalOnProperty(jffast.social.enabled=true)` 控制，未启用时控制器不注册，故排除不产生暴露）。新增 `SocialAnonymousEndpointContractTest` 钉住两件事：authorize/callback 匿名可达，且 `bind` / `unbind` / `binds` **仍需登录态**（不能整段 `/social/**` 放行）
- 请假申请读写越权：写侧仅在新增时写 `userId`，读侧与改删侧此前**没有任何归属校验**（`page` 不带 userId 过滤、`detail` 直接 `getById`、`update` 直接 `updateById`、`delete` 直接 `removeById`）⇒ 任何持有 `demo:leave:*` 的角色可读全表请假事由，并按任意 id 改写、删除他人的申请。归属校验下沉到 `LeaveApplicationService`（`getOwnedById` / `updateOwned` / `deleteOwned`），非超管只可见本人；`updateOwned` 同时锁死 `userId` / `userName` / `status`，不接受客户端提交
- 出参不再直接返回实体：新增 `SysUserVO` / `SysUserRoleVO` / `SysUserDeptVO`，用户列表、用户详情、登录、获取用户信息四处改用 VO。此前 `dataScope`（授权结构）、`role_id` 原始串、`dept_ids`、`create_by` 等内部字段随响应外发，后续新增实体字段还会自动外泄；密码与盐此前已用 `WRITE_ONLY` 挡住。会话内仍存完整 `SysUser`（Sa-Token 反序列化白名单不受影响）
- 魔法错误码收敛：`SysUserController` / `SysTaskController` 共 18 处 `R.error(403|400, …)` 改为 `AuthErrorCode.FORBIDDEN` / `FrameworkErrorCode.PARAM_ERROR`（**下发数值一个未变**，客户端契约不变）；`AuthErrorCode` 补 `FORBIDDEN(403)`
- 部门备注可维护：`SysDeptRequest` 有 `remark`，但 `SysDept` 实体与 `sys_dept` 表**都没有这一列** ⇒ 前端填的备注在 `BeanUtil.copyProperties` 后被静默丢弃（用户填了等于没填）。补 `SysDept.remark` 字段与 `sys_dept.remark` 列
- 开发态启动横幅：新增 `DevProfileGuard`，未以 prod 启动时在启动日志里逐条列出开发态暴露项（公开初始口令 `test1234`、Swagger 默认开放、数据源与 Redis 走本机默认值、dev 日志级别）与生产部署的正确姿势，消除「忘设 `SPRING_PROFILES_ACTIVE` 即静默以开发态上生产」的风险。容器形态的硬把关仍由 `jffast-boot/Dockerfile` 的 ENTRYPOINT 承担（未设 profile 直接 exit 1）
- 文档口径订正：`SysFileController` 注释原写「抛业务异常由全局处理器转成错误状态码」——实际全局处理对 `BusinessException` 一律返回 HTTP 200 + 业务码，而前端 `shared/src/utils/request.ts` 的 blob 分支**正是靠这一点**识别下载失败并弹错（改成 404 会命中前端「404 不弹全局提示」分支 ⇒ 下载失败变成零提示）。注释改为如实描述并写明「不要改成 404/500」的原因。`release-info.md` 与 `docker-compose.yml` 不再复制 SQL 清单（改指向 README 的唯一权威，此前漏过 `sys_post` / `sys_task` / `sys_file` 三份，漏导则菜单永不出现），flowable 建表脚本明确为**必须导入**；README 中「必须显式激活 dev」与「默认激活 dev」的自相矛盾段落已订正
- 用户名 / 手机号唯一约束：`sys_user` 建表语句补 `uk_user_name` / `uk_phone`，新增与改名路径加分布式锁，重复用户名不再依赖并发窗口；唯一约束冲突（`TooManyResultsException` / `DataIntegrityViolationException`）转译为可读的业务错误。表结构一律内建在 `sql/system_sql.sql` 的建表语句里（项目尚未发布，不存在需要升级的存量库）
- 用户导入 `realName` 必填且逐行校验：姓名为空或超长按**行**失败并写明原因，不再整批 `saveBatch` 因 NOT NULL 违例回滚；导入模板表头补「姓名必填」说明
- 通知发送统计分页与计数同源：`isRead` / `userName` 过滤下推到 SQL，页脚总数与列表一致（此前在内存里过滤分页后的数据，`total` 与 `list` 不同源）
- fat jar 内嵌 `META-INF/LICENSE` + `META-INF/NOTICE`（Apache-2.0 §4(a)(d) 要求二进制分发随附许可证副本），并排除非 OSI 许可的 Oracle JDBC / 达梦 / 人大金仓驱动与 PostgreSQL 驱动（官方发行 jar 统一只带 MySQL 驱动，切换数据库需自行构建，见 README「多数据库支持」）
- 用户管理越权与契约（Base 专项评审批）：新增接口拒绝请求体自带 `id`；更新接口强制 `id` 且目标必须存在；非超管不得授予超管角色、不得创建或改名 `admin`、不得修改/删除/重置超管账号；删除用户清理 `sys_user_role` / `sys_user_dept` 并踢下线且禁止自删；角色列表仅显式提交时替换并校验角色存在
- 重置密码：管理员重置改用 `SysResetPasswordRequest`（仅 `userId`），生成随机强口令并一次性返回 `{userId,userName,password}`，重置后踢下线；修改本人密码的 `SysPasswordRequest` 移除 `userId` 字段（目标恒为登录态）
- MinIO 下载/删除全部失败：`replace(endpoint + "/", "")` 后的 `/bucket/key` 被首个 `/` 切成空 bucket，统一对象键解析并补单测覆盖 URL 形态
- AI 会话归属：详情/消息/删除按当前用户过滤（超管放行），创建时写入 `userId`
- 权限缓存永久不失效：角色-菜单绑定、菜单权限变更后统一失效 `role:perms:{roleId}` 与历史无 TTL 哈希条目；`LoginUtil` 权限集合去重，超管权限走带缓存的全集
- 参数与资源：配置 DTO 移除客户端可写的 `systemBuiltin`，更新校验存在与结果、键变更清旧缓存；日志清理天数下限 1；缓存管理仅允许 `jffast:` 命名空间且禁止通配键；Excel 导入流自动关闭并限制 10000 行；通知接收人 ID 安全解析（非数字返回参数错误）；通知派发改到事务提交后执行
- 数据权限拦截器解析/注入失败改为 fail-closed（拒绝查询）；幂等冲突改抛业务异常；无权限以业务码 403 返回（HTTP 状态仍为 200）；XSS 过滤器补齐请求头清洗
- 流程任务/实例越权：见 `FlowAccessService`；`WfCompleteTaskRequest` 补参数校验
- 社交登录：state 增加签名与过期校验，绑定接口改用服务端票据，回调返回 `bindTicket` 并与前端契约对齐
- 定时任务调用目标默认白名单由整个 `cn.jfspeed` 包收敛为空（仅允许 `@JfFastJob` 方法），非注解任务需显式配置 `jffast.job.invoke.allowed-packages`
- 「生成代号」端点参数名错位：`SysRoleController.createDictCode` 的形参写死 `roleName`，而角色与字典两处调用方传的都是 `dictName`，Spring 永远绑不上 → 历史 NPE / 500。形参统一为 `dictName`，拼音初始化与 null / 生僻名兜底收口到 `PinyinCodeUtil`（`Bopomofo4j.local()` 静态块必须先于 Hutool 首次建引擎执行）
- Sa-Token session 白名单漏配 `SysDept`：登录写入 session 的 `SysUser` 自带 `deptList(List<SysDept>)`，1.46 白名单拒收会导致「登录成功但取不到用户」（有部门归属的用户必现、无部门的 admin 不现）。各应用 `application.yml` 的 `sa-json-allow-types` 已补齐
- 删除角色恒 500 且列表行不消失：`SysMenuService.deleteRolePerm` 把 `Long roleId` 直接当 Redis hash key 传给 `hdel`，而写入端（`insertRoleMenu` / `updateRolePerm`）用的是 `roleId + ""`（String）——`StringRedisSerializer` 拒收 Long 抛 `ClassCastException`，异常冒泡让整个 `DELETE /sys/role/{id}` 返回 500（浏览器实测：提示服务异常、行仍在）。改为 `String.valueOf(roleId)`，与写入端 key 类型一致
- 代码生成器包名兜底：`generate()` 未处理空 `packageName`，会拼出非法包名 `cn.jfspeed..entity`（双点，产物不可编译）。空值落回 `demo`（与示例模块 `jffast-demo` / `cn.jfspeed.demo` 约定一致），新增单测 `CodeGeneratorTest#blankPackageFallsBackToDemo` 锁住该不变量
- 代码生成器：`Request` DTO 始终包含 `id` 字段——修改接口的 `@DistributedLock(key = "'x:' + #req.id")` 与 `updateById(entity)` 都依赖主键，此前缺失会导致 update 端点 SpEL 求值报错（500）
- 代码生成器：`crud` / `tree` / `masterDetail` 三类 Controller 的每个标准接口补 `@SaCheckPermission`（权限标识 `{module}:{pageName}:{view|add|update|delete}`，并放行 `super_admin`），与自动生成的菜单 SQL（C 菜单 + 四个 F 按钮）权限标识一一对应，满足接口权限门禁
- 角色-菜单绑定静默失效：`SysRole.menuIds` / `deptIds` 原为 `transient` 字段，Controller 的 `SysRoleRequest → SysRole` 转换（Hutool `BeanUtil.copyProperties` 跳过 `transient`）后恒为 `null`，保存角色即静默清空 `sys_role_menu` 绑定，导致除超管外所有角色用户 403。改用 `@TableField(exist = false)`，新增 `SysRoleRequestMappingTest` 锁住该不变量
- 安全加固（巡检批）：本地文件上传拒绝主动内容扩展名（html / svg / js 等）；`/upload/*` 响应头补 `nosniff` 与 CSP sandbox；`/sys/user/all` 补 `@DataPermission`；`ErrorLogAspect` 复用 `LogAspect.maskSensitiveInfo` 脱敏；生产库连接默认 `sslMode=${MYSQL_SSL_MODE:REQUIRED}`；流程部署校验资源名白名单与 BPMN 根元素（防路径穿越 / XXE）
- 代码生成器（巡检批补充）：注释 / 建表 SQL / 菜单 SQL 的动态文本按目标语言转义（Java 字符串转义 / SQL 字面量转义）；子表名与包名防路径穿越；主子表 Controller 补 `@RequestBody @Valid`；子节点删除失败返回 `R.error`
- 部署栈缺失 prod 必填项：`JFFAST_INIT_PASSWORD` 从未注入（prod profile 下无默认值，缺失即启动失败），compose 与 Helm values 一并补齐
- 部署栈 MySQL 握手失败：prod 的 JDBC url 默认 `sslMode=REQUIRED`，而 compose 内 MySQL 未启用 TLS，首次启动卡在握手 —— compose 补 `MYSQL_SSL_MODE`（默认 `PREFERRED`）可调
- 菜单种子主键撞号导致三个模块菜单静默消失：`system_init_data.sql` 新增的「系统监控 / 缓存管理 / OAuth2 接入配置」复用了 `1012/1013/1014` 与 `2101-2104`，而这三号已被可选模块脚本 `sys_post.sql`（岗位管理）、`sys_task.sql`（异步任务）、`sys_file.sql`（文件管理）占用；模块脚本用 `INSERT IGNORE`，按 README 顺序导入时**后导入的一方整行被丢弃且不报错**，表现为「岗位/异步任务/文件管理菜单永远不出现」。三个新页面改用 `1015-1017`，按钮用 `2401 / 2501-2502 / 2601-2604`（`sort` 同步顺延 15/16/17），并在 SQL 内登记段位分配说明
- springdoc 3 接口文档恒 500（`NoSuchMethodError: Schema.$dynamicRef`）：非 jakarta 构型的 `swagger-annotations`（2.2.28，knife4j 时代遗留）与 `swagger-annotations-jakarta` 注解**包名完全相同**（`io.swagger.v3.oas.annotations`），两者同时在 classpath 时 fat jar 中旧版胜出，而 `$dynamicRef()` 2.2.30 才引入 ⇒ `/v3/api-docs` 恒 500、Swagger UI 打不开接口定义。BOM 统一收敛 `swagger-annotations-jakarta:2.2.55`（与 springdoc 3 传递的 `swagger-core-jakarta` 对齐）；新增 `SwaggerAnnotationsFlavorTest`（胜出 jar 构型线 + 版本线双断言）防回归
- 角色更新边界：`id` 缺省或目标不存在返回「角色不存在」（不再静默新增/部分写入）；`syncMenus=true && menuIds=null` 保持原菜单并告警（不再清空）；`deptIds=null` 保持绑定；`dataScope != 5` 时清空部门绑定；仅授权字段变化才发布会话失效事件
- 角色重名漏检：`checkRoleNameAndKey` 在 `setId` 之前执行，新建时排除 ID 为空导致重名校验失效，改为先落 ID 再校验
- 用户写路径：`roleList`/`deptList` 的 null=不修改、`[]`=清空；`roleList != null` 时显式写 `sys_user.role_id`（`[]` 写空串，避免 `NOT_EMPTY` 策略跳过导致旧角色残留）；非空 `dept_id` 必须存在；创建路径不发布会话失效事件
- 用户管理越权：详情/更新/删除/重置密码/解锁先按数据范围取目标用户，越权返回 403「用户不存在或无权操作」；删除时清理 `sys_user_role`/`sys_user_dept` 并踢下线
- 异步导出注解元数据：`resolve` 缺失方法/注解时 fail-closed，创建任务前中止，不再退化成全量导出
- 代码生成器实体注解包名：`@IgnoreBaseField` 改用 `cn.jfspeed.common.core.annotation`（原生成 `starter-mybatis` 注解，未引入该 starter 的模块编译失败）
- 任务归属缺陷：普通用户可见他人任务、任务详情泄露服务器文件路径、下载路径可被 DB 篡改指向任意文件
- demo 档启动失败（开源前复审批）：`application-demo.yml` 作为 profile 专属文档写了 `spring.profiles.include: dev`，Spring Boot 2.4+ 对这类文档里的该属性直接判非法 —— 实测 `--spring.profiles.active=demo` 与 `demo,dev` 两种传法都抛 `InvalidConfigDataPropertyException`，而 README 把「一键起演示站」当头条能力宣传。改为在 `application.yml`（非专属文档）用 `spring.profiles.group.demo: [dev]` 做组内展开。新增 `ProfileActivationContractTest` 钉住三件事：任何 `application-*.yml` 都不得出现 `spring.profiles.include/active`、`spring.profiles.group` 的展开目标必须有对应文件、demo 档确实打开 `jffast.demo-mode.enabled`；断言走 YAML 结构而非文本正则（`management.endpoints.web.exposure.include` 也是 `include:`，正则会把合法配置判成违规）。回归验证：demo 档实起 `Started JfFastApplication`，`/actuator/health` 返回 UP
- 分页无单页封顶（开源前复审批）：`PaginationInnerInterceptor.maxLimit` 默认 null = 不限条数，任意列表接口传 `pageSize=1000000` 就能一次读全表。改为默认夹到 500，并由 `jffast.mybatis.page-max-limit` 显式配置（批量导出走导出接口，不调高此项）。新增 `PaginationMaxLimitContractTest` 从自动装配产物里取出分页拦截器断言封顶值，防止将来重排拦截器链时把 `setMaxLimit` 丢掉
- 部门备注「一编辑就丢」（开源前复审批）：`SysDeptMapper.xml` 的两条查询 SELECT 都没有 `remark` 列 ⇒ 前端编辑弹窗回填时 `remark` 恒为空，提交即把已有备注覆盖掉。两条 SELECT 补 `remark`
- 用户分页出参回到实体（开源前复审批）：`/sys/user/page` 走 `SysUserMapper.xml` 的 `select u.*`，出参是 `SysUser` 实体 ⇒ `tenantId` / `updatePwdTime` / `isBusinessUser` 等内部字段随响应外发（`password` / `salt` 已有 `WRITE_ONLY` 挡住）。改用 `SysUserVO`，与服务层此前声明的「列表出参用 VO」口径对齐
- 字典 `is_type` 拆箱 NPE（开源前复审批）：DDL 里该列可空，而 3 处判断写成 `sysDict.getIsType() == 1`（Integer 自动拆箱），历史数据行为 NULL 时字典树/列表直接 500。收敛为 `SysDict.isDictType(dict)` 空值安全判断
- 分页工具拆箱：`PageUtil.getPageMp(pageReq)` 直取 `getPageNo()/getPageSize()`，缺参即 NPE；改为复用 `PageReq#getPage()` 的兜底逻辑
- BPMN 流式部署绕过 XXE 预校验：`FlowService.deploy(..., InputStream)` 只校验了资源文件名，XML 内容直接交给引擎；字符串重载才做安全 DOM 解析。流式重载改为读满后走同一套 `validateBpmnXml`
- 死代码：`SysRoleService.existsRoleCode` 无任何调用方（重名校验走 `checkRoleNameAndKey`，且有 `SysRoleCreateDuplicateCheckTest` 覆盖），删除
- 仓库内私有叙事清理（开源前复审批）：源码注释与 CHANGELOG 里的内部缺陷编号、跨仓同步脚本路径、旧域名等叙述改为对外可读的表达；`docs/` 与 `deploy/` 的链接一律指向本仓可达路径（此前引用的 `jffast.cc` 文档站域名未上线，`security@jfspeed.cc` 不解析 ⇒ 漏洞上报渠道实际失效，SECURITY.md 改为只走 GitHub 私密漏洞报告）
- `LICENSE` 恢复为 Apache-2.0 **官方未修改正文**：此前正文被改写过（§4(b) "those notices" → "any notices"、§9 删去 "or different" 许可选项、APPENDIX 整段删除），与 README / NOTICE 声称的 Apache-2.0 不一致；改写版许可证不受 OSI 承认，等于「声称 Apache-2.0 实际不是」。现与 `https://www.apache.org/licenses/LICENSE-2.0.txt` 逐字节一致（11357 字节；复原后仍留有一个前导空行，也一并删掉——差 1 个字节的许可证严格讲仍是"被修改过的"）
- `mvnw` 与 `deploy/gen-rsa.sh` 补执行位（100755）：Windows 上不影响使用，Linux / macOS 克隆后 `./mvnw` 直接 `permission denied`，无 Maven 的读者第一步就走不通

### 变更

- 用户导入模板由 5 列变为 6 列（末列「归属部门」）：**5 列旧模板对非超管逐行失败：归属部门不能为空**（超管仍可导入，留空即不设归属部门）；导出文件列数保持 5 列不变
- `/sys/user/resetPassword` 响应由明文字符串改为 `{userId,userName,password}` 对象；`/social/bind` 入参改为 `{ticket}`；`GET /social/callback` 必须携带服务端签发的 `state` 并返回 `bindTicket`
- 文件下载失败不再返回 HTTP 200（改 404/500）；删除文件先删存储对象再删记录，存储失败时保留记录可重试
- 缓存管理接口仅接受 `jffast:` 前缀的缓存名，`clearAll` 只清应用缓存命名空间，不再覆盖 Sa-Token 会话 / 限流计数 / 分布式锁
- 定时任务调用白名单默认值变更（见「破坏性变更」）
- 新增规范测试：`CodeGeneratorTest`（生成产物含 `id` / `@SaCheckPermission` / Request DTO）、`DistributedLockSpelContractTest`（注解散列化 SpEL 的 `#参数名` 必须是方法真实参数名）；`ArchitectureConventionTest` 扩充三条（`@TableName` 实体须继承 `BaseEntity`、Controller 不得依赖 Mapper、禁用 `printStackTrace`）
- 后端 Dockerfile 收敛为一份（删除 `deploy/Dockerfile`，保留 `jffast-boot/Dockerfile`）：去掉硬编码的 starter 模块清单，改用 `.dockerignore` 控制构建上下文 —— 新增或改名模块不再需要同步改 Dockerfile；jar 文件名改通配；运行身份改为非 root 并预建可写的 `/app/upload`
- 后端 compose 的 SQL 播种改文件级挂载 + `01/02` 前缀定序：此前把整个 `sql/` 挂进 `initdb.d`，会被 initdb 全量执行，且按文件名排序不等于依赖顺序
- 角色更新接口 `PUT /sys/role/update` 的 `id` 必填且目标必须存在；`menuIds`/`deptIds` 的 null/`[]` 语义明确为「不修改/清空」
- 用户新增/更新接口收紧角色与部门授予（非超管、自身角色/部门不可变）；`deptId=0` 或不存在部门被拒绝（无部门请传 `null`）
- 用户删除会同时清理 `sys_user_role`/`sys_user_dept` 并踢下线
- 异步任务列表/详情/下载/删除的可见范围按创建人隔离（超管全量）

### 破坏性变更

- 用户写接口：`deptList: []` 清空原有非空绑定、归属部门不在绑定集合、创建时显式绑定不含 `deptId`，现在一律返回业务码 403（此前可写入零绑定或分叉数据）；存量零绑定账号的纯字段编辑仍放行
- 管理员重置密码：`POST /sys/user/resetPassword` 请求体改为 `{"userId": ...}`（不再需要 `oldPassword`），响应从字符串改为 `{userId,userName,password}`；`POST /sys/user/updatePassword` 不再接受 `userId` 字段
- 社交账号绑定：`POST /social/bind` 请求体改为 `{"ticket": "..."}`，票据只能取自 `GET /social/callback` 的响应；回调 `state` 必须是服务端签发且 10 分钟内有效
- 定时任务：`jffast.job.invoke.allowed-packages` 默认值由 `cn.jfspeed` 改为空，只有标注 `@JfFastJob` 的方法可直接调度，其他任务需显式追加所在包前缀
- `AESUtil` 的环境变量名 `JP_FAST_AES_KEY` / `JP_FAST_AES_IV` → **`JFFAST_AES_KEY` / `JFFAST_AES_IV`**（品牌统一时 `JPFAST_* → JFFAST_*` 的漏改项；系统属性名 `jffast.aes.key` / `jffast.aes.iv` 未变）。仍设旧变量名的部署会因取不到密钥而**启动即抛** `IllegalStateException: AES 密钥未配置`（不会静默换钥解密出乱码），改名即可
- 用户部门字段：`deptId=0` 或指向不存在部门将返回 400「部门不存在」，无部门必须传 `null`/不传（`sys_user.dept_id` 默认 NULL）
- 角色写接口：非超管创建/更新 `dataScope=1/5`、授予超出自身菜单、修改自己所属角色一律拒绝；角色更新仅在授权字段变化时撤销绑定用户会话
- 异步任务可见范围：普通用户只能看到/下载/删除自己创建的任务（历史 `create_by=0` 归超管）

## [1.1.0] — 2026-10-11

### 新增

- 岗位管理：`sys_post` 表 + `SysPost` 实体/Service/Controller + 「系统管理 → 岗位管理」页面与菜单种子（`sql/sys_post.sql`），岗位编码唯一校验
- 文件管理：`sys_file` 表 + `SysFileController`（分页/下载/批量删除）+ 「系统管理 → 文件管理」页面（`sql/sys_file.sql`）；上传由 `jffast-starter-file` 自动登记文件记录（历史文件不回填）
- 文件存储多云适配：新增**阿里云 OSS**（`AliyunOssFileStorageService`）与**腾讯云 COS**（`TencentCosFileStorageService`），与既有的本地 / MinIO 并列，按 `jffast.file.storage.type` 切换；SDK 按 `@ConditionalOnClass` 隔离，不引入即不装配
- 异步任务：`sys_task` 表 + `SysTaskController`/`SysTaskService`（导入/导出任务进度跟踪、结果文件下载）+ 「系统管理 → 异步任务」页面（`sql/sys_task.sql`），用户导入导出改为异步执行
- Excel 导入导出增强：用户/日志导入导出、导入模板下载、导入结果明细（失败行与原因）
- 动态数据源：`jffast-starter-mybatis` 新增 `@DS` 注解与切面（`jffast.datasource.dynamic.*`，默认关闭），支持运行时按方法切换数据源
- 通知通道：站内信之外新增**邮件通道**（走 `spring.mail`）与**短信通道**（通用 HTTP 网关，SPI 可替换），由 `NotificationChannelDispatcher` 统一分发；通知发送页面
- WebSocket：握手 token 支持子协议传递（保留 query 兜底）；业务回调改由 `WebSocketEventHandler` SPI 注册，不再改框架代码
- 定时任务：任务归属改为注解声明式，`sys_job` 增 `service_name` 列（已直接内建于 `sql/system_sql.sql` 建表语句）
- 工作流：`AssigneeTaskListener` 支持按任务级 BPMN 扩展属性（`flowable:assigneeType/assigneeValue`）解析审批人——优先任务级属性（流程设计器画布产出，逐任务生效），回退流程变量（业务层发起时写入，兼容既有接入）；新增引擎转换器保留扩展属性的契约测试
- 零配置体验：`spring.profiles.default=dev`（clone 后 `mvn spring-boot:run` 直接跑通）；admin 默认初始口令改为统一公开测试值 `test1234`（prod profile 覆盖为 `JFFAST_INIT_PASSWORD` 必填，缺失启动失败；使用公开值初始化时启动日志打 WARN）；全部配置默认口令 / 示例命令统一为 `test1234`
- 安全：配置文件凭据扫描加入 pre-commit（白名单仅 `${ENV}` 占位符与 `test1234`），泄露扫描改为 fail-closed（扫描自身出错拒绝提交）

### 修复

- 登录验证码：`SaTokenConfig` 免登录白名单补 `/sys/needCaptcha`，修复「连续输错 2 次后要求验证码、但登录页永远不显示验证码」导致的登录卡死
- XSS 过滤：`XssHttpServletRequestWrapper` / `XssUtil` 收敛与单测补齐

## [1.0.0] — 2026-09-07

首个公开版本，包含后端基础框架与系统管理能力。

### 新增

**基础框架**

- Spring Boot 3.5.x + JDK 21，启用虚拟线程
- MyBatis-Plus 持久层 + Sa-Token 鉴权 + Knife4j 接口文档
- `jffast-bom` 统一依赖版本管理
- `jffast-common` 通用基础：统一响应 `R<T>`、异常体系、`BaseEntity`、工具类、可视化代码生成器

**Starter 体系**

| Starter | 能力 |
|---------|------|
| `mybatis` | 多数据源、分页插件、字段填充、多数据库方言 |
| `web` | 统一异常处理、链路追踪 TraceId、XSS 过滤、限流、Actuator |
| `security` | Sa-Token 集成、登录鉴权、验证码 |
| `redis` | Redis 封装与常用操作 |
| `log` | 操作日志、登录日志 |
| `file` | 文件存储（本地 / MinIO / 阿里云 OSS / 腾讯云 COS） |
| `excel` | Excel 导入导出 |
| `social` | GitHub / Gitee 第三方登录 |
| `job` | Quartz 定时任务持久化管理 |
| `flowable` | Flowable 7 工作流引擎门面封装 |

**业务模块**

- `jffast-system`：用户、角色、菜单、部门、岗位、字典、参数、通知、日志
- `jffast-flow`：流程分类、流程定义、流程实例、待办、已办
- `jffast-demo`：业务模块开发示例

**其他**

- 多数据库支持：MySQL / PostgreSQL / Oracle / SQL Server / 达梦 / 人大金仓
- 登录密码 RSA 加密传输；密钥不入库，dev/test 启动时自动生成一次性密钥对，生产环境必须显式注入
- 部署支持：Dockerfile、docker-compose、Helm Chart

### 安全

- 生产环境默认关闭接口文档，可通过 `knife4j.basic.auth` 配置访问控制
