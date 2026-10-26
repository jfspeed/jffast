# 安全策略

## 支持版本

| 版本 | 支持状态 |
|------|----------|
| 尚未发布 | ⏳ 首个正式版本发布后，此处将列出受支持的版本与分支 |

> 本项目**尚未正式发布**（当前为 `1.0.0-SNAPSHOT`），因此还没有"受支持版本"这一概念。
> 首个正式版本发布后，会在此列出受支持的版本与分支；仅最新 minor 分支获得安全修复，请尽量升级到最新版本。

## 报告漏洞

请**不要**通过公开 GitHub Issue 报告安全漏洞。

请使用 [GitHub Security Advisories](https://github.com/summerZstar/jffast/security/advisories/new) 提交私密报告（需在仓库 Settings → Security 打开 "Private vulnerability reporting"）。本仓库不设邮箱渠道——写死的邮件地址一旦域名失效，上报就会石沉大海，而 GitHub 私密报告有送达记录。

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
