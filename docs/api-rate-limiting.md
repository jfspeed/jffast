# API 限流配置指南

## 概述

jffast 使用 [Resilience4j](https://resilience4j.readme.io/) 实现接口限流，通过 `@RateLimit` 注解轻松为接口添加限流保护。

## 快速开始

### 1. 添加注解

在 Controller 方法上添加 `@RateLimit` 注解：

```java
import cn.jfspeed.security.annotation.RateLimit;

@RestController
@RequestMapping("/api/example")
public class ExampleController {

    @RateLimit(value = "default")
    @PostMapping("/create")
    public R<Void> create(@RequestBody ExampleDTO dto) {
        // ...
    }
}
```

### 2. 配置限流参数

在 `application.yml` 中配置限流参数：

```yaml
resilience4j:
  ratelimiter:
    instances:
      default:
        limitForPeriod: 10        # 一个周期内允许的请求数
        limitRefreshPeriod: 1s    # 周期长度
        timeoutDuration: 0        # 等待超时时间（0 表示立即拒绝）
```

## 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | "default" | 限流器名称，对应配置中的 `instances` 键 |

## 配置说明

### limitForPeriod

一个周期内允许通过的最大请求数。超过此数量的请求将被拒绝。

```yaml
default:
  limitForPeriod: 10  # 每秒允许 10 个请求
```

### limitRefreshPeriod

周期刷新时间。与 `limitForPeriod` 配合使用：

```yaml
default:
  limitForPeriod: 100
  limitRefreshPeriod: 1m  # 每分钟允许 100 个请求
```

### timeoutDuration

当限流器满时，等待获取令牌的超时时间：

```yaml
default:
  limitForPeriod: 10
  limitRefreshPeriod: 1s
  timeoutDuration: 500  # 等待 500ms，超时则拒绝
```

## 多限流器配置

可以为不同接口配置不同的限流策略：

```yaml
resilience4j:
  ratelimiter:
    instances:
      default:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
        timeoutDuration: 0
      login:
        limitForPeriod: 5
        limitRefreshPeriod: 1s
        timeoutDuration: 0
      export:
        limitForPeriod: 3
        limitRefreshPeriod: 1m
        timeoutDuration: 0
```

使用时指定限流器名称：

```java
// 使用默认限流器
@RateLimit(value = "default")
public R<Void> create() { }

// 使用登录限流器
@RateLimit(value = "login")
public R<Void> login() { }

// 使用导出限流器
@RateLimit(value = "export")
public R<Void> export() { }
```

## per-IP 限流（jffast.security.*-ip-limit）

`@RateLimit` 用的是 resilience4j **全局共享令牌桶**，没有 IP 维度：单个来源打满它就会让所有人 429。
登录、验证码这类「匿名可达 + 可被单点滥用」的接口，另有一层**按客户端 IP 分桶**的限流，与全局桶叠加。

| 场景 | 接口 | 配置前缀 | 默认阈值 |
|------|------|----------|----------|
| 登录 | `POST /sys/login` | `jffast.security.login-ip-limit` | 30 次 / 60 秒 |
| 验证码下发 | `GET /sys/getCode` | `jffast.security.captcha-ip-limit` | 10 次 / 60 秒 |
| 验证码需求查询 | `GET /sys/needCaptcha` | `jffast.security.captcha-check-ip-limit` | 60 次 / 60 秒 |

```yaml
jffast:
  security:
    login-ip-limit:
      enabled: true
      max-attempts: 30
      window-seconds: 60
    captcha-ip-limit:
      enabled: true
      max-attempts: 10
      window-seconds: 60
    captcha-check-ip-limit:
      enabled: true
      max-attempts: 60
      window-seconds: 60
```

要点：

- **统计口径含成功**：登录限流统计全部尝试（成功也计），只计失败会让撞库「蒙对即清零」。
- **窗口随首次尝试滑动**：仅首次计数设 TTL，避免固定窗口边界被踩点；key 若因异常失去 TTL 会补设一次，避免该 IP 被永久封禁。
- **失效策略为 fail-closed**：判定依赖的 Redis 不可用时**直接拒绝**（返回 429）——这几个场景本身已依赖 Redis（验证码 / 失败计数），而「Redis 挂掉」正是攻击者等待的窗口。
- **超限响应**：`429` + 场景文案（与全局桶的通用文案区分，便于运维定位）。

### 部署在反向代理之后：必须配置 trusted-proxies

`X-Forwarded-For` 是**客户端可控**的请求头。为既拿到真实客户端 IP、又不被伪造头绕过限流，
框架只在 **TCP 对端地址命中 `jffast.security.trusted-proxies`** 时才逐跳回溯转发头，否则一律使用 TCP 对端地址。

```yaml
jffast:
  security:
    trusted-proxies:
      - 172.16.0.0/12   # Docker 默认 bridge 网段
      - 127.0.0.1       # 宿主直跑 / 同机 nginx
```

也可用环境变量注入（逗号分隔）：

```bash
JFFAST_SECURITY_TRUSTED_PROXIES=172.16.0.0/12,127.0.0.1
```

> 未配置时默认「不信任任何转发头」。容器化部署（自带 nginx 反代后端）若漏配，**所有请求会被判定成同一个 IP**，per-IP 限流退化成全站一个桶——既起不到防护，又容易误伤真实用户。

## 限流响应

当请求被限流时，返回 HTTP 429 状态码：

```json
{
  "code": 429,
  "message": "请求过于频繁，请稍后再试",
  "data": null
}
```

## 最佳实践

### 1. 不同操作使用不同限流器

```java
// 查询接口：较高限制
@RateLimit(value = "query")
public R<PageRes> page() { }

// 新增接口：中等限制
@RateLimit(value = "create")
public R<Void> create() { }

// 删除接口：较低限制
@RateLimit(value = "delete")
public R<Void> delete() { }
```

### 2. 敏感操作使用独立限流器

```java
// 登录接口：严格限制
@RateLimit(value = "login")
@PostMapping("/login")
public R<Void> login() { }

// 注册接口：严格限制
@RateLimit(value = "register")
@PostMapping("/register")
public R<Void> register() { }
```

### 3. 导出/批量操作使用宽松限流器

```java
// 导出接口：宽松限制
@RateLimit(value = "export")
@GetMapping("/export")
public void export() { }
```

## 监控与告警

### Actuator 端点

启用 Actuator 后，可以通过以下端点监控限流器状态：

```
GET /actuator/health/rateLimiters
GET /actuator/ratelimiters
```

### 指标

Resilience4j 提供以下指标：

- `resilience4j.ratelimiter.waiting_threads` - 等待线程数
- `resilience4j.ratelimiter.available_permissions` - 可用令牌数
- `resilience4j.ratelimiter.refreshed_permissions` - 已刷新令牌数

## 常见问题

### Q: 限流器不生效？

A: 检查以下几点：

1. 确认 `@RateLimit` 注解正确添加
2. 确认 `resilience4j-ratelimiter` 依赖已引入
3. 确认 `application.yml` 中配置了对应的限流器实例

### Q: 如何动态调整限流参数？

A: 可以通过以下方式：

1. **重启应用**：修改配置后重启
2. **Config Server**：使用 Spring Cloud Config 动态刷新
3. **Nacos**：配置中心动态推送

### Q: 分布式环境下如何限流？

A: 当前实现是单机限流。分布式限流建议：

1. **网关层限流**：使用 Sentinel 或 Spring Cloud Gateway 的限流滤镜
2. **Redis 限流**：自定义 `RateLimiter` 实现，基于 Redis 计数器
3. **Sentinel**：使用阿里巴巴 Sentinel 实现分布式限流

## 示例配置

### 完整配置示例

```yaml
resilience4j:
  ratelimiter:
    instances:
      # 默认限流器
      default:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
        timeoutDuration: 0
      
      # 登录限流器
      login:
        limitForPeriod: 5
        limitRefreshPeriod: 1s
        timeoutDuration: 0
      
      # 注册限流器
      register:
        limitForPeriod: 3
        limitRefreshPeriod: 1m
        timeoutDuration: 0
      
      # 导出限流器
      export:
        limitForPeriod: 3
        limitRefreshPeriod: 1m
        timeoutDuration: 500
      
      # 查询限流器
      query:
        limitForPeriod: 50
        limitRefreshPeriod: 1s
        timeoutDuration: 0
```

## 相关文档

- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [Spring Cloud Gateway 限流](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/request-rate-limiting.html)
- [Sentinel 限流](https://sentinelguard.io/zh-cn/docs/flow-control.html)