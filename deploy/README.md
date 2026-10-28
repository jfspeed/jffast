# jffast 部署指南

本文档介绍 jffast 的部署方式，按场景选择：

| 方式 | 适用场景 | 复杂度 |
|------|---------|--------|
| [Docker（单容器）](#方式一docker-单容器) | 快速体验、本地开发 | ⭐ |
| [Docker Compose](#方式二docker-compose) | 单机生产、Demo 环境 | ⭐⭐ |
| [Kubernetes + Helm](#方式三kubernetes--helm) | 生产集群、弹性伸缩 | ⭐⭐⭐ |
| [Kubernetes 原生 Manifests](#方式四kubernetes-原生-manifests) | 不用 Helm 的团队 | ⭐⭐⭐ |
| [独立部署](#独立部署只取镜像不用-compose) | 只要镜像，自己写编排 | ⭐⭐ |

> 后端与前端是**两个独立镜像**（前一个装 jar，后一个装 dist + nginx），
> 由同一份编排拉起 —— 不要把前后端打进同一个镜像：改前端要重新编译后端，发版粒度会被绑死。

---

## 前置条件

### 1. 准备 RSA 密钥对

登录密码加密用，**必须配置**，且**前后端必须是同一对**：前端用公钥加密登录密码，后端用私钥解密。
不成对时服务能正常启动，只有登录必失败，很难定位。

一次生成、同时给出两半：

```bash
bash deploy/gen-rsa.sh
```

输出三行：`RSA_PRIVATE_KEY` / `RSA_PUBLIC_KEY` 给后端，第三行 `VITE_RSA_PUBLIC_KEY` 给前端。

没有 openssl 时用项目自带的 Java 工具（格式由 `RSAUtil` 保证）：

```java
import cn.jfspeed.common.security.util.RSAUtil;
RSAUtil.generateKey();  // 返回 [publicKey, privateKey]
```

> ⚠️ 密钥不要提交进仓库：私钥泄露等于所有人的登录密码可被解密。

### 2. 初始化数据库

**导入哪些文件、按什么顺序**，以仓根 [README.md「快速开始 → 2. 初始化数据库」](../README.md#2-初始化数据库)
的 SOURCE 列表为唯一权威（14 条，按行序即序号 1–14，`--` 注释掉的可选行同样占序号）；
本指南不复制一份（两处清单必然漂移）。
本机 MySQL 的命令形态：

```bash
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/<清单里的文件>.sql
```

部署侧另有两条必须知道的事实：

- `flowable_schema/flowable_mysql_all.sql` **必须导入，且要在首次启动前**：引擎启动即初始化，
  而同实例别的库带 `ACT_*` 时它不会自建表，缺表即以 `Table 'xxx.act_ge_property' doesn't exist` 启动失败。
- 清单里带菜单种子的脚本（`sys_post.sql` / `sys_task.sql` / `sys_file.sql` / `sys_notification_template.sql`）
  不能省：前三者的前端菜单在各自脚本里播种（不在 `system_init_data.sql`），缺表会在点开菜单时报 `Unknown table`。

### 3. 准备镜像

后端与前端是**两个独立镜像**，各自单独构建：

| 镜像 | 构建命令（在对应仓库根目录执行） |
|------|--------------------------------|
| 后端 jffast-boot | `cd jffast && docker build -f jffast-boot/Dockerfile -t jffast-boot:1.0.0 .` |
| 前端 jffast-web | `cd jffast-ui && docker build --build-arg VITE_RSA_PUBLIC_KEY=$PUB -t jffast-web:1.0.0 .` |

也可以不手动构建，交给 compose 自动 build（见方式二）。

---

## 方式一：Docker 单容器

最快体验方式。先起 MySQL + Redis：

```bash
cd jffast
docker compose up -d   # 启动 mysql + redis
```

导入 SQL（清单与顺序同上：以仓根 README「快速开始 → 2. 初始化数据库」为唯一权威，此处不复制）。
容器内 MySQL 用管道逐条导入，命令形态：

```bash
docker exec -i jffast-mysql mysql -uroot -ptest1234 jffast < sql/<清单里的文件>.sql
```

> ⚠️ 别在 `docker exec -it` 进容器后用 `SOURCE sql/xxx.sql`：文件在宿主机，容器里没有，
> 会静默失败。用上面的管道形式（与 `SOURCE` 等价，且无需 `docker cp`）。

启动 jffast-boot：

```bash
docker run -d --name jffast-boot \
  --network host \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e MYSQL_HOST=127.0.0.1 \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD=test1234 \
  -e REDIS_HOST=127.0.0.1 \
  -e RSA_PRIVATE_KEY="<your-private-key>" \
  -e RSA_PUBLIC_KEY="<your-public-key>" \
  -e JFFAST_INIT_PASSWORD="<admin 初始密码>" \
  jffast-boot:1.0.0
```

> ⚠️ **`SPRING_PROFILES_ACTIVE` 是必填项**：数据源 / Redis / RSA 只存在于 `application-{dev,test,prod}.yml`，
> 镜像不再内置任何库名或口令默认值；ENTRYPOINT 未拿到该变量会**立即退出并打印提示**（fail-fast）。
> prod profile 下 `MYSQL_HOST`、`MYSQL_USER`、`MYSQL_PASSWORD`、`REDIS_HOST`、`RSA_PRIVATE_KEY`、
> `JFFAST_INIT_PASSWORD` 均为必填，缺失会以占位符解析失败的形式在启动阶段报错。

验证后端：http://localhost:7200/jffast-api/actuator/health
（prod 默认关闭 swagger，接口文档只在 dev/test 下访问 `/jffast-api/swagger-ui.html`；
context-path 来自 `server.servlet.context-path=/${app.name}-api`）

---

## 方式二：Docker Compose（推荐）

后端栈（MySQL + Redis + 后端）与前端栈（nginx + dist）**分属两个仓库、两份 compose**，
通过外部网络 `jffast-net` 互通。这样两个仓库各自单仓就能用，谁也不依赖兄弟目录。

### 1. 建一次共享网络

```bash
docker network create jffast-net
```

### 2. 起后端栈（jffast 仓库）

```bash
cd jffast
cp deploy/.env.example deploy/.env     # 填 RSA 密钥对与 JFFAST_INIT_PASSWORD
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

首次启动按 `deploy/docker-compose.yml` 的 `01`–`14` 自动播种。那份挂载序与仓根 README
「快速开始 → 2. 初始化数据库」SOURCE 列表（14 条，按行序即序号 1–14）**逐行同序**
——编号 = 那份清单的行序序号。默认全量：示例模块、AI 知识库、社交绑定都在内
（2026-10-29 与 README「13 条默认执行」对齐；此前只挂 01–08）。不需要这些可选模块的部署，
把编排里 `09`–`14` 对应行注释掉即可，注释不影响其余编号与顺序。
启动后访问：http://localhost:7200/jffast-api/swagger-ui.html

> ⚠ **首启后必须核对播种真的跑完了。** MySQL 官方镜像的 initdb 是「任一脚本报错即中止」，
> 而 `restart: unless-stopped` 会在容器退出后**立刻把它拉起** —— 第二次启动因数据目录已非空，
> **跳过全部播种**。结果：`docker ps` 显示 healthy、healthcheck 也通过，但表/菜单缺失
> （接口 500、登录后没有菜单）。
>
> **预期规模**：默认 `01`–`14` 播种完是 **89 张表 / 142 条菜单 / 39 张 `ACT_*`**；
> 若把 `09`–`14` 注释掉再在空卷上重播，则是 **80 / 107 / 39**。数字对不上，基本就是某个脚本报错
> 让整条链被跳过（`docker logs` 里能看到第一个 `ERROR`）。
>
> 核一眼就够：
>
> ```bash
> docker exec jffast-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" \
>   -e "SHOW TABLES FROM \`jffast\` LIKE 'sys_menu'"
> docker logs jffast-mysql | grep ERROR      # 报错明细在这里
> ```
>
> 查不到就删卷重来：`docker compose down -v` 后再 `up -d`。
> 自己补挂 SQL 时同理：**被播种的文件里不要留裸 `ALTER TABLE … ADD COLUMN`**（新库的
> `CREATE TABLE` 已含该列 → Duplicate column name → 其后全部脚本一起被跳过）；
> 存量升级另建幂等的 `*_migrate_*.sql`（先查 `information_schema` 再 ALTER），且不挂进 initdb。

### 3. 起前端栈（jffast-ui 仓库）

```bash
cd ../jffast-ui
cp deploy/.env.example deploy/.env     # 填 VITE_RSA_PUBLIC_KEY（必须与后端同一对）
docker compose -f deploy/docker-compose.yml --env-file deploy/.env up -d --build
```

访问：http://localhost:7201

### 4. 停止并清理

```bash
docker compose down -v   # -v 删除数据卷；不删卷则下次启动不会重跑 SQL 播种
```

> ⚠ **`down` 的作用域是「项目名」，而默认项目名 = 编排文件所在目录名。**
> 本仓后端 / 前端两份编排都在 `deploy/` 目录 ⇒ 默认同为 `deploy`，一边执行
> `down --remove-orphans` 就会把另一边的容器当孤儿删掉。两份编排现已各自显式声明
> `name:`（`jffast-backend` / `jffast-ui`）。
> 把本编排复制到自己的项目时**必须换成自己的唯一名字**；改名后旧的 `deploy_*` 卷不会被自动接管
> （看着像数据没了），按 compose 文件头的一次性命令搬卷，或删旧卷让它重新播种。

> **容器名不受 `-p` 影响。** 三个服务显式声明了 `container_name`（默认 `jffast-mysql` /
> `jffast-redis` / `jffast-backend`）。同一台宿主机已有同名容器时，只换 `-p <项目名>` 照样
> `Conflict`；在 `.env` 里设 `CONTAINER_NAME_PREFIX=<唯一前缀>-`（如 `acme-`）即可整套改名并存。

> **prod 的必填项**：`MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` / `REDIS_HOST` /
> `RSA_PRIVATE_KEY` / `RSA_PUBLIC_KEY` / `JFFAST_INIT_PASSWORD`。
> 前六项 compose 已注入，RSA 密钥对和 `JFFAST_INIT_PASSWORD`（admin 初始密码）必须由你生成后写进 `.env`。
> 缺失时 compose **直接拒绝启动**，而不是让容器先起来再用「登录失败」暴露问题。

---

## 独立部署：只取镜像，不用 compose

compose 只是一种用法。两个镜像都是自包含、全部配置走环境变量的，
可以直接推到自己的镜像仓库，用 K8s 或任何编排单独部署。

### 两个跨镜像契约（自己拼装时必须保证）

| 契约 | 说明 | 破坏后果 |
|------|------|---------|
| API 前缀 | 前端产物里的 `VITE_API_URL`（默认 `/jffast-api`）必须等于后端 `server.servlet.context-path`（`/${app.name}-api`） | 页面能打开，所有接口 404 |
| RSA 密钥对 | 前端构建期注入的 `VITE_RSA_PUBLIC_KEY` 与后端运行时的 `RSA_PRIVATE_KEY` 必须成对 | 服务正常，登录必失败 |

改了 `app.name` 就会改 context-path，此时**前端镜像必须重建**（`--build-arg VITE_API_URL=/xxx-api`）。

### 构建期变量 vs 运行时变量

| 变量 | 阶段 | 改了要重建镜像吗 |
|------|------|-----------------|
| `VITE_API_URL` / `VITE_BASE_URL` / `VITE_ENABLE_MULTI_TENANT` / `VITE_RSA_PUBLIC_KEY` | 构建期（`build --build-arg`） | **要** |
| `BACKEND_UPSTREAM` / `API_PREFIX` | 运行时（容器 env，nginx 启动时渲染） | 不要 |

`VITE_*` 由 `import.meta.env` 在 build 时静态替换进产物 —— 容器启动后再改 env 不会生效。
但 `VITE_API_URL` 默认是同源相对路径，只要反代前缀不变，同一个镜像能直接部署到任意域名。

### K8s 注意事项

- **文件存储**：`jffast.file.storage.type` 默认 `local`，落在容器内的 `/app/upload`。
  多副本时上传只落在某个 Pod 本地，其它副本读不到就是 404，Pod 重建文件也会丢。
  解决：只跑单副本 + PVC，或改成 `minio` / `aliyun-oss` / `tencent-cos`（starter 已内置四种实现）。
- **定时任务**：`@JfFastJob` 按 `sys_job.service_name` 归属调度，每次触发再抢一次分布式执行锁。
  配了 Redis 时锁是 Redis 实现（多副本只跑一次）；**没配 Redis 会降级成单 JVM 锁**并在启动时打印 WARN，
  同一服务的多个副本会各执行一次 —— 所以后端必须配 Redis。
- **Ingress**：`/jffast-api` 路由到后端 Service 时必须转发 WebSocket 升级头
  （nginx-ingress 需对应注解），否则站内通知永远收不到，且前端不会报错。
- **前端镜像的 `RESOLVER`**：镜像内的 nginx 在**运行时**解析上游主机名（不是启动即解析），
  所以必须给一个可用的 DNS。Docker 默认 `127.0.0.11`；**K8s 必须改成集群 DNS**
  （例如 `10.96.0.10`，见 Pod 内 `/etc/resolv.conf`），否则所有接口 502。
- **SQL 播种**：把 `sql/` 挂进 `/docker-entrypoint-initdb.d` 依赖 MySQL 官方镜像的机制，只在 compose 下生效；
  K8s 请用 Job / initContainer / migration 自行导入。

---

## 方式三：Kubernetes + Helm

### 前置

- K8s 集群 1.24+
- `kubectl` 已配置集群上下文
- `helm` 3.10+
- 集群默认 StorageClass 可用（用于 MySQL/Redis 持久化）
- （可选）Ingress Controller（nginx-ingress / traefik）

### 1. 拉取 Bitnami 依赖

```bash
cd deploy/helm/jffast
helm dependency update
```

### 2. 自定义 values

```bash
cp values.yaml my-values.yaml
```

修改 `my-values.yaml` 关键项：

```yaml
image:
  repository: jffast/jffast-boot
  tag: "1.0.0"

secrets:
  MYSQL_PASSWORD: "<strong-password>"       # 必改
  RSA_PRIVATE_KEY: "<your-private-key>"      # 必填
  RSA_PUBLIC_KEY: "<your-public-key>"        # 必填

mysql:
  auth:
    rootPassword: "<strong-password>"        # 与上同
  primary:
    persistence:
      storageClass: "your-storage-class"

ingress:
  enabled: true
  className: "nginx"
  hosts:
    - host: jffast.example.com
      paths:
        - path: /
          pathType: Prefix
```

### 3. 安装

```bash
kubectl create namespace jffast
helm install jffast ./deploy/helm/jffast \
  -n jffast \
  -f my-values.yaml
```

### 4. 查看部署状态

```bash
kubectl -n jffast get pods
kubectl -n jffast get svc
kubectl -n jffast logs -f deployment/jffast
```

### 5. 升级

```bash
helm upgrade jffast ./deploy/helm/jffast \
  -n jffast \
  -f my-values.yaml
```

### 6. 卸载

```bash
helm uninstall jffast -n jffast
# 注意：PVC 默认不删，需手动清理
kubectl -n jffast delete pvc -l app.kubernetes.io/instance=jffast
```

### 配置外部数据库（不用 Chart 内 MySQL/Redis）

```yaml
mysql:
  enabled: false    # 不部署 MySQL 子 chart

redis:
  enabled: false    # 不部署 Redis 子 chart

secrets:
  MYSQL_HOST: "your-external-mysql.host"
  MYSQL_PORT: "3306"
  MYSQL_USER: "jffast"
  MYSQL_PASSWORD: "<your-password>"
  REDIS_HOST: "your-external-redis.host"
  REDIS_PORT: "6379"
  REDIS_PASSWORD: "<your-password>"
```

### 生产环境清单

- [ ] `replicaCount >= 2`（多副本高可用）
- [ ] `autoscaling.enabled: true`（HPA 弹性伸缩）
- [ ] `resources.limits` 按节点容量调整
- [ ] `ingress.enabled: true` + TLS 证书
- [ ] `secrets.MYSQL_PASSWORD / REDIS_PASSWORD` 使用强密码
- [ ] `secrets.RSA_PRIVATE_KEY / RSA_PUBLIC_KEY` 已配置
- [ ] `JFFAST_SWAGGER_ENABLED: "false"`（关闭接口文档）
- [ ] MySQL/Redis 用外部托管服务或独立 StatefulSet
- [ ] PVC 备份策略已就绪（Velero / 快照）

---

## 方式四：Kubernetes 原生 Manifests

不用 Helm 的团队可直接用 K8s manifests。最小化部署示例：

```yaml
# jffast-k8s.yaml
---
apiVersion: v1
kind: Namespace
metadata:
  name: jffast
---
apiVersion: v1
kind: Secret
metadata:
  name: jffast-secret
  namespace: jffast
type: Opaque
stringData:
  MYSQL_HOST: "your-mysql.host"
  MYSQL_PASSWORD: "test1234"
  REDIS_HOST: "your-redis.host"
  RSA_PRIVATE_KEY: "<your-private-key>"
  RSA_PUBLIC_KEY: "<your-public-key>"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jffast-boot
  namespace: jffast
spec:
  replicas: 2
  selector:
    matchLabels:
      app: jffast-boot
  template:
    metadata:
      labels:
        app: jffast-boot
    spec:
      containers:
        - name: jffast-boot
          image: jffast-boot:1.0.0
          ports:
            - containerPort: 7200
          env:
            - name: MYSQL_HOST
              valueFrom:
                secretKeyRef: { name: jffast-secret, key: MYSQL_HOST }
            - name: MYSQL_PASSWORD
              valueFrom:
                secretKeyRef: { name: jffast-secret, key: MYSQL_PASSWORD }
            # ... 其他环境变量
            - name: JAVA_OPTS
              value: "-Xms256m -Xmx768m"
          readinessProbe:
            httpGet: { path: /jffast-api/actuator/health, port: 7200 }
            initialDelaySeconds: 30
          livenessProbe:
            httpGet: { path: /jffast-api/actuator/health, port: 7200 }
            initialDelaySeconds: 90
---
apiVersion: v1
kind: Service
metadata:
  name: jffast-boot
  namespace: jffast
spec:
  selector:
    app: jffast-boot
  ports:
    - port: 7200
      targetPort: 7200
```

部署：

```bash
kubectl apply -f jffast-k8s.yaml
```

---

## CI/CD 集成

### GitHub Actions 自动构建镜像

参考 [.github/workflows/release.yml](../.github/workflows/release.yml)，已有 GHCR 镜像构建，可按需扩展。

### Helm 部署到 K8s（CD 示例）

```yaml
# .github/workflows/deploy.yml 片段
- name: Deploy to K8s
  run: |
    helm upgrade --install jffast ./deploy/helm/jffast \
      --namespace jffast --create-namespace \
      --set image.tag=${{ github.ref_name }} \
      --set secrets.MYSQL_PASSWORD=${{ secrets.MYSQL_PASSWORD }} \
      --set secrets.RSA_PRIVATE_KEY=${{ secrets.RSA_PRIVATE_KEY }} \
      --set secrets.RSA_PUBLIC_KEY=${{ secrets.RSA_PUBLIC_KEY }}
```

---

## 故障排查

### Pod 启动失败

```bash
# 查看日志
kubectl -n jffast logs <pod-name>
# 查看事件
kubectl -n jffast describe pod <pod-name>
```

常见原因：
- `RSA_PRIVATE_KEY` 未配置 → **服务能正常启动，但登录必然失败**（`RSAUtil.decryptPwd` 抛「RSA私钥未配置」；`JfFastProperties` 只是普通 `@ConfigurationProperties`，缺值不会让启动失败，所以此前很容易被误判为"启动即崩"）。该变量只在 `application.yml` 的 `jffast.rsa` 中被读取，因此**必须注入该环境变量**（或 `secrets.RSA_PRIVATE_KEY`），只改 Dockerfile 的 ENV 无效
- `SPRING_PROFILES_ACTIVE` 未配置 → **启动即退出**（fail-fast），日志首行含 `[FATAL] SPRING_PROFILES_ACTIVE is not set`
- `MYSQL_HOST` 连接超时 → 检查 Secret 配置和网络策略
- `JFFAST_SWAGGER_ENABLED=true` → 生产环境建议关闭（文档入口无独立鉴权，靠网关/反向代理控制访问）

### 数据库连接拒绝

```bash
# 进入 Pod 测试
kubectl -n jffast exec -it <pod-name> -- sh
wget -qO- http://jffast-mysql:3306  # 应返回 MySQL 错误页（连接成功）
```

### 健康检查失败

健康端点：`/jffast-api/actuator/health`。需 `spring-boot-starter-actuator`（已在 `jffast-boot/pom.xml` 中声明）。

```bash
kubectl -n jffast port-forward svc/jffast 7200:7200
curl http://localhost:7200/jffast-api/actuator/health
```

应返回 `{"status":"UP"}`。

---

## Quartz 集群注意

单体默认 `job-store-type: jdbc` + `isClustered: "true"` + `instanceId: AUTO`，即**开箱即集群**：
多个实例共用同一套 `QRTZ_*`，靠行锁保证同一时刻只有一个实例取走同一 trigger。

**只有在你把集群关掉时才需要担心**（`isClustered: "false"` 或 `instanceId: NON_CLUSTERED`）：
那样多实例之间没有任何互斥，同一 trigger 可能被多个 JVM 重复触发。

```yaml
spring:
  quartz:
    job-store-type: jdbc
    properties:
      org:
        quartz:
          scheduler:
            instanceId: AUTO               # 集群必须用 AUTO（NON_CLUSTERED 只能单实例）
          jobStore:
            isClustered: "true"            # 关掉 = 多实例重复触发
            clusterCheckinInterval: "20000" # 心跳间隔 ms，默认 20000
```

`instanceName` 是 Quartz 的**行分区键**（`QRTZ_*.SCHED_NAME`）：同库不同应用必须不同名，
否则两个应用会被当成同一个集群互相抢 trigger。多应用部署时请为每个应用配置不同的 `instanceName`。

---

## 相关文档

- [Base 后端 README](../README.md)
- [多数据库支持（连接 URL 速查）](../README.md#多数据库支持)
- [开发规范](../docs/开发规范.md)
- [演示模式](../docs/demo-mode.md)
