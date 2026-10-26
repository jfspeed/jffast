# 工作流引擎 Flowable 8 Base 基础版

> 模块：`jffast-starter-flowable` + `jffast-flow`
> 引擎：Flowable 8.0.0
> 版本：Base 基础版（2026-07-25 完成）

## 功能概述

Base 版集成 Flowable 8 BPMN 2.0 工作流引擎，提供完整的流程生命周期管理：

| 能力 | 说明 |
|------|------|
| 流程部署 | 支持 BPMN XML 字符串或文件部署，支持加载已部署流程作为新版起点 |
| 流程定义 | 分页查询、查看 XML、查看流程图（PNG）、挂起/激活、删除（级联） |
| 流程实例 | 发起流程（含业务主键 + JSON 流程变量）、查询运行中实例、取消实例、查看审批历史 |
| 任务管理 | 我的待办、任务池（候选未签收）、签收、审批（含意见 + 流程变量）、转办 |
| 历史查询 | 我的已办、历史流程实例、流程实例审批历史 |
| 流程设计器 | 可视化拖拽（bpmn-js 画布 + 右侧属性面板，含审批人类型/取值）+ XML 代码模式（双向同步） |
| 审批人解析 | SPI 接口 `AssigneeResolver`，支持 user/role/dept/initiator 四种类型 |

## 架构设计

### 模块职责

```
jffast-starter-flowable（Starter，零业务依赖）
├── config/        FlowableAutoConfiguration + FlowableProperties
├── constant/      FlowConstant（扩展属性键、审批人类型、流程变量键）
├── listener/      AssigneeTaskListener（任务创建时解析审批人）
├── resolver/      AssigneeResolver（SPI 接口，业务模块实现）
└── service/       FlowService（门面，封装 Repository/Runtime/Task/History）

jffast-flow（业务模块，依赖 jffast-starter-flowable + jffast-system）
├── controller/    WfCategoryController / WfProcessController / WfTaskController / WfHistoryController
├── entity/        WfCategory（流程分类，对应 Flowable process category）
├── mapper/        WfCategoryMapper
├── service/       WfCategoryService
├── resolver/      JfFastAssigneeResolver（实现 AssigneeResolver，用 SysUserService 解析 role/dept → userIds）
└── domain/dto/    WfDeployRequest / WfStartProcessRequest / WfCompleteTaskRequest
```

### SPI 设计：AssigneeResolver

Starter 不依赖 `jffast-system`，无法直接查询用户/角色/部门关系。通过 SPI 接口解耦：

```java
// Starter 定义接口（cn.jfspeed.flowable.resolver）
public interface AssigneeResolver {
    List<String> resolveUserIds(String type, String value);
}

// 业务模块实现（cn.jfspeed.flow.resolver）
@Service
public class JfFastAssigneeResolver implements AssigneeResolver {
    @Resource private SysUserService sysUserService;

    @Override
    public List<String> resolveUserIds(String type, String value) {
        // user → 直接返回 value 列表
        // role → 查 sys_user.role_id like roleId 的所有用户
        // dept → 查 sys_user.dept_id in deptIds 的所有用户
        // initiator → 由调用方处理（从流程变量取）
    }
}
```

`FlowableAutoConfiguration` 通过 `ObjectProvider<AssigneeResolver>` 注入，未提供实现时只支持 `user`/`initiator`，提供后支持 `role`/`dept`。

### 审批人配置

在 BPMN UserTask 上添加扩展属性：

```xml
<userTask id="managerApprove" name="主管审批"
          flowable:assigneeType="role"
          flowable:assigneeValue="manager"/>
```

| 类型 | value 格式 | 解析结果 |
|------|-----------|---------|
| `user` | 用户ID列表，逗号分隔（如 `1,2,3`） | 直接作为办理人/候选人 |
| `role` | 角色 key（如 `manager`） | 查询该角色下所有用户 |
| `dept` | 部门ID列表，逗号分隔（如 `100,200`） | 查询部门下所有用户 |
| `initiator` | 忽略 | 解析为流程发起人（从流程变量 `initiatorUserId` 取） |

- 单个用户：`taskService.setAssignee(taskId, userId)` 设置为办理人
- 多个用户：`taskService.addCandidateUser(taskId, userId)` 添加为候选人（任务池模式，需签收后才能审批）

**解析优先级**：`AssigneeTaskListener` 在任务创建时，优先读取该 UserTask 上的上述**任务级扩展属性**（流程设计器可视化面板产出的就是该形态，逐任务生效）；任务上未配置时回退读取**流程变量** `assigneeType` / `assigneeValue`（业务层在发起流程或前置任务完成时写入，兼容既有接入）。模型读取异常等场景一律安全回退，不阻塞任务创建。

## 配置说明

### application.yml

```yaml
jffast:
  flow:
    enabled: true  # Starter 总开关

flowable:
  # 引擎 schema 校验/增量升级开关；**空库不能靠它自动建表**（Flowable 8 会把完全空库误判为
  # 旧版本走升级路径而失败），首次部署必须先导入 sql/flowable_schema/flowable_mysql_all.sql
  database-schema-update: true   # 保持 true：用于版本校验与后续升级
  history-level: audit           # 历史级别：none/activity/audit/full
  async-executor-activate: true  # 启用异步执行器（定时事件/异步任务需要）
  # 只用 process 引擎；Flowable 8 的 process starter 默认把 event-registry 也带入，
  # 不关会与 process 引擎并发建表/升级抢 schema.version 行（实测启动即 FlowableOptimisticLockingException）
  eventregistry:
    enabled: false
  process:
    definition-cache-limit: 100
  cmmn: { enabled: false }       # 关闭不需要的引擎模块（保持轻量）
  dmn:  { enabled: false }
  app:  { enabled: false }
  idm:  { enabled: false }
```

### 数据库表

| 表 | 来源 | 说明 |
|---|------|------|
| `ACT_*` / `FLW_*` | `sql/flowable_schema/flowable_mysql_all.sql`（**首次部署必须先导入**） | Flowable 8 引擎核心表（部署/定义/实例/任务/变量/历史等 45 张）。**不要依赖 `database-schema-update` 在空库建表**：Flowable 8.0.0 会把完全空库误判为旧版本、走升级路径而失败（实测 FlowableOptimisticLockingException） |
| `wf_category` | `sql/flow_sql.sql` | 流程分类表（业务扩展） |

执行顺序：`system_sql.sql` → `system_init_data.sql` → `flowable_schema/flowable_mysql_all.sql` → `flow_sql.sql`

## API 接口

### 流程分类 `/flow/category`

| 路径 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/page` | GET | `flow:category:view` | 分页查询 |
| `/list` | GET | `flow:category:view` | 查询全部启用分类（下拉用） |
| `/detail/{id}` | GET | `flow:category:view` | 分类详情 |
| `/create` | POST | `flow:category:add` | 新增 |
| `/update` | PUT | `flow:category:update` | 修改 |
| `/{id}` | DELETE | `flow:category:delete` | 删除 |

### 流程定义/部署/实例 `/flow/process`

| 路径 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/deploy` | POST | `flow:process:deploy` | 部署 BPMN XML |
| `/deployment/{deploymentId}` | DELETE | `flow:process:deploy` | 删除部署（级联） |
| `/definition/page` | GET | `flow:process:view` | 流程定义分页（最新版本） |
| `/definition/{id}/xml` | GET | `flow:process:view` | 获取 BPMN XML |
| `/definition/{id}/diagram` | GET | `flow:process:view` | 获取流程图（PNG） |
| `/definition/{id}/suspend` | PUT | `flow:process:deploy` | 挂起流程定义 |
| `/definition/{id}/activate` | PUT | `flow:process:deploy` | 激活流程定义 |
| `/instance/start` | POST | `flow:process:start` | 发起流程实例 |
| `/instance/page` | GET | `flow:process:view` | 运行中实例分页 |
| `/instance/{processInstanceId}` | DELETE | `flow:process:cancel` | 取消实例 |

### 任务管理 `/flow/task`

| 路径 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/todo/page` | GET | `flow:task:view` | 我的待办（分页） |
| `/candidate/page` | GET | `flow:task:view` | 我的候选任务（任务池） |
| `/claim/{taskId}` | POST | `flow:task:claim` | 签收任务 |
| `/complete` | POST | `flow:task:complete` | 完成任务（审批） |
| `/delegate/{taskId}` | POST | `flow:task:delegate` | 转办任务 |
| `/detail/{taskId}` | GET | `flow:task:view` | 任务详情 |
| `/history/{processInstanceId}` | GET | `flow:task:view` | 任务审批历史（按流程实例） |

### 历史查询 `/flow/history`

| 路径 | 方法 | 权限 | 说明 |
|------|------|------|------|
| `/done/page` | GET | `flow:task:view` | 我的已办（分页） |
| `/instance/page` | GET | `flow:process:view` | 历史实例分页 |
| `/instance/{id}/tasks` | GET | `flow:process:view` | 实例审批历史 |
| `/instance/{id}` | GET | `flow:process:view` | 历史实例详情 |

## 前端页面

| 菜单 | 路径 | 文件 | 功能 |
|------|------|------|------|
| 流程分类 | `/flow/category/index` | `pages/flow/category/index.vue` + `save.vue` | 分类 CRUD |
| 流程定义 | `/flow/definition/index` | `pages/flow/definition/index.vue` | 部署/挂起/激活/删除/XML/流程图 |
| 流程实例 | `/flow/instance/index` | `pages/flow/instance/index.vue` | 发起/取消/审批历史 |
| 待办任务 | `/flow/task/todo` | `pages/flow/task/todo.vue` | 待办/任务池/签收/审批/转办 |
| 已办任务 | `/flow/task/done` | `pages/flow/task/done.vue` | 已办列表/审批历史 |
| 流程设计器 | `/flow/designer/index` | `pages/flow/designer/index.vue` + `flowable-moddle.ts` | 可视化（bpmn-js）+ XML 双模式，审批人属性经 moddle 扩展读写 |

前端 API：`src/api/flow.js`（category / process / task / history + loadDiagramObjectUrl）

## 菜单初始化

菜单 ID 段：`3000-3099`（避免与现有菜单冲突），见 `sql/flow_sql.sql`：

```
3000 工作流管理（目录）
├── 3001 流程分类（C）  flow:category:view
│   ├── 3011 分类查询  flow:category:view
│   ├── 3012 分类新增  flow:category:add
│   ├── 3013 分类修改  flow:category:update
│   └── 3014 分类删除  flow:category:delete
├── 3002 流程定义（C）  flow:process:view
│   ├── 3021 流程部署  flow:process:deploy
│   ├── 3022 流程挂起  flow:process:deploy
│   ├── 3023 流程激活  flow:process:deploy
│   └── 3024 流程删除  flow:process:deploy
├── 3003 流程实例（C）  flow:process:view
│   ├── 3031 发起流程  flow:process:start
│   └── 3032 取消实例  flow:process:cancel
├── 3004 待办任务（C）  flow:task:view
│   ├── 3041 签收任务  flow:task:claim
│   ├── 3042 审批任务  flow:task:complete
│   └── 3043 转办任务  flow:task:delegate
├── 3005 已办任务（C）  flow:task:view
└── 3006 流程设计器（C） flow:process:deploy
```

> 该段位是踩过两次主键冲突后用实测占用定下的（原 1500/2000 段均与 `system_init_data.sql` 冲突），
> 详见 `sql/flow_sql.sql` 文件头。新增菜单前先 `SELECT id, parent_id, name FROM sys_menu ORDER BY id` 核对占用。

超级管理员（`role_id=1`）通过 `@SaCheckPermission(orRole = BaseConstant.ROLE_SUPER_ADMIN)` 自动拥有所有权限，无需在 `sys_role_menu` 表配置。

## 使用示例

### 1. 部署一个请假流程

进入「工作流管理 → 流程设计器」，粘贴以下 XML：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
             xmlns:flowable="http://flowable.org/bpmn"
             targetNamespace="http://jffast.io/flow">
  <process id="leave" name="请假流程" isExecutable="true">
    <startEvent id="start" name="开始"/>
    <sequenceFlow id="flow1" sourceRef="start" targetRef="managerApprove"/>
    <userTask id="managerApprove" name="主管审批"
              flowable:assigneeType="role"
              flowable:assigneeValue="manager"/>
    <sequenceFlow id="flow2" sourceRef="managerApprove" targetRef="hrRecord"/>
    <userTask id="hrRecord" name="HR备案"
              flowable:assigneeType="role"
              flowable:assigneeValue="hr"/>
    <sequenceFlow id="flow3" sourceRef="hrRecord" targetRef="end"/>
    <endEvent id="end" name="结束"/>
  </process>
</definitions>
```

填写部署名称「请假流程 v1」、分类「请假流程」、资源文件名「leave.bpmn20.xml」，点击「部署」。

### 2. 发起流程

进入「工作流管理 → 流程实例」，点击「发起流程」：
- 选择流程定义：请假流程 v1
- 业务主键：`LEAVE-2026-001`（可选，对应请假单 ID）
- 流程变量：`{"days":3,"reason":"事假"}`

### 3. 审批任务

被指派为审批人（角色为 `manager` 的用户）进入「工作流管理 → 待办任务」：
- 看到任务后点击「审批」
- 填写审批意见「同意」
- 可选填写流程变量（如指定下一节点审批人）
- 点击「提交审批」

## 后续扩展方向（不在 Base 基础版范围内）

1. **加签/会签/或签** — 多人审批模式（当前只支持单人/候选人池）
2. **流程版本对比** — 不同版本的 BPMN diff
3. **流程监控大屏** — 实时统计待办/超时/通过率
4. **业务表单绑定** — 流程节点关联动态表单
5. **流程催办/委托** — 超时自动催办、长期未办委托

## 扩展方向

- `jffast-flow` 业务模块可通过 Maven 依赖直接引入（`cn.jfspeed:jffast-flow`），也可参考其实现自行扩展
