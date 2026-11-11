# jffast-starter-demo-mode

演示模式 starter：把站点变成只读演示站——拦截 DB 写操作（短路返回）、拦截文件上传等副作用、暂停 Quartz 调度并阻止手动触发任务。

## 启用方式

自动装配默认关闭，仅在 `jffast.demo-mode.enabled=true` 时生效（bean 都不建，正常环境零开销）：

```yaml
jffast:
  demo-mode:
    enabled: true   # 仅演示 profile 打开
    exempt-statement-prefixes:   # 豁免的 Mapper 前缀，写操作正常入库
      - SysLogMapper
      - SysJobLogMapper
```

## 适用栈说明（重要）

本 starter 适配 jffast 全栈 boot——jffast boot 自带 `jffast-starter-mybatis` 与 `jffast-starter-job`，开箱即用。

**独立引入方注意**：本 starter 的两项核心能力依赖以下模块，需自备，缺失时对应能力不生效（不会报错，静默缺失）：

| 依赖模块 | 支撑的能力 | 缺失时 |
|---|---|---|
| `jffast-starter-mybatis` | MyBatis Executor 写短路 | 写操作不被拦截，DB 可写 |
| `jffast-starter-job` | Quartz Scheduler 类型（`org.quartz.Scheduler`） | 调度暂停不生效，任务照常执行 |

副作用切面（文件上传等）基于 AspectJ，随 spring-boot-starter-aspectj 自动生效。

## 组成

| 组件 | 作用 |
|---|---|
| `DemoModeWriteBlockInterceptor` | MyBatis 拦截器，非豁免 DML 一律短路返回 0 |
| `DemoModeSideEffectAspect` | 拦截文件上传等副作用入口 |
| `DemoModeSchedulerPauser` | 暂停 Quartz 触发并拦截手动触发 |
