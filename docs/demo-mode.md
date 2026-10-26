# 上线演示站（演示模式）

> 能力：`jffast-starter-demo-mode`（默认关闭）。线上演示站“增删改可操作、返回成功，但不入库”。

## 开启

后端：

```bash
cd jffast/jffast-boot
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

JAR 部署：`java -jar jffast-boot.jar --spring.profiles.active=demo`。
开关位置：`jffast-boot/src/main/resources/application-demo.yml`

前端：构建时注入 `VITE_DEMO_MODE=true`（顶栏横幅 + 登录页提示），如：

```bash
VITE_DEMO_MODE=true npm run build
```

## 拦截清单

| 副作用 | 行为 | 说明 |
|--------|------|------|
| DB 写（insert/update/delete） | 短路返回 0，业务照常 `R.success` | MyBatis Executor 层；查询不受影响 |
| 操作/任务日志 | 照写 | 豁免 `SysLogMapper`、`SysJobLogMapper`（`exempt-statement-prefixes` 可配），审计不断 |
| 文件上传/删除 | 拒绝并明示 | `FileStorageService` 切面抛业务异常（无“假成功”，否则前端状态错乱） |
| 定时任务 | 不调度 + 拒绝手动触发 | 启动 `pauseAll`；`Scheduler.triggerJob` 切面拒绝；任务 CRUD 页面照常用 |
| 代码生成器落盘 | 本就拒绝 | JAR 运行时 `CodeGenerator.isRunningFromJar()` 直接 `R.error`，无需本 starter |
| 短信 | log 提供方 | Base 无短信，演示模式下短信走 log 占位 |
| AI 对话等第三方调用 | 不拦截 | 按量计费接口请自行关闭入口或限流后再上线演示站 |

## 已知边界

- 自增 ID / 雪花 ID 照常消耗（短路不回滚发号器），无害。
- “删后回查”类接口（如删完读详情）会 404，前端按正常异常展示。
- `@DistributedLock` / 事务照常走，短路发生在 executor 层，空事务正常提交，无残留。
- 演示站建议配只读 DB 账号做第二道保险（本 starter 是第一道）。

## 适用范围（既定约束 2026-10-02）

- 可运行应用已接线，开演示站只换 profile 即可。
- 本 starter 默认不引入：如某个基于本框架的独立项目确需演示站，手动在其 boot pom
  加 `jffast-starter-demo-mode` 依赖并自备 `application-demo.yml` 即可。

## 配置项

```yaml
jffast:
  demo-mode:
    enabled: true
    exempt-statement-prefixes:
      - SysLogMapper
      - SysJobLogMapper
```
