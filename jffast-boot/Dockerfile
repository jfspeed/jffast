# jffast-boot Dockerfile（Base 后端）
# 多阶段构建：Maven 编译 → JRE 运行
#
# 构建上下文必须是 jffast/ 仓库根目录（jffast-bom / jffast-common / jffast-starters /
# jffast-biz / jffast-boot 缺一不可 —— jffast-boot 依赖前面所有模块）：
#
#   cd jffast
#   docker build -f jffast-boot/Dockerfile -t jffast-boot:latest .
#
# 关于依赖缓存：早期版本在这里逐个 COPY 各模块的 pom.xml 预热 Maven 缓存，代价是
# **把 starter 清单硬编码进了 Dockerfile** —— 每新增或改名一个模块，忘了同步这里就会
# 漏 COPY，且要等到编译期才炸。现改为直接 COPY 整个工程 + 用 .dockerignore 排除
# target/ 与 .git 控制体积，换取「模块增删零维护」。
#
# 运行（必须显式指定 profile，否则容器立即退出 —— 数据源只存在于 application-{dev,test,prod}.yml）：
#
#   docker run -d --name jffast-boot -p 7200:7200 \
#     -e SPRING_PROFILES_ACTIVE=prod \
#     -e MYSQL_HOST=... -e MYSQL_USER=... -e MYSQL_PASSWORD=... \
#     -e REDIS_HOST=... \
#     -e RSA_PRIVATE_KEY=... -e RSA_PUBLIC_KEY=... \
#     -e JFFAST_INIT_PASSWORD=... \
#     jffast-boot:latest
#
# 变量要求：
#   - prod：MYSQL_HOST / MYSQL_USER / MYSQL_PASSWORD / REDIS_HOST、
#           RSA_PRIVATE_KEY / RSA_PUBLIC_KEY / JFFAST_INIT_PASSWORD 全部必填
#           （application-prod.yml 里这些占位符没有默认值，缺失即启动失败）
#   - dev ：可全部省略（application-dev.yml 内置 127.0.0.1 默认值；RSA 未配置时
#           进程内自动生成一次性密钥对，重启即失效，仅适合本机联调）
#   - 多副本：RSA 密钥对必须各实例一致，否则登录请求打到不同实例会解密失败

# ─── Stage 1: Maven 构建 ───
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 整工程拷入：不挑模块，避免 Dockerfile 与模块清单双份漂移
COPY . .
RUN mvn -B -DskipTests -pl jffast-boot -am clean package

# ─── Stage 2: JRE 运行 ───
FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    addgroup -S jffast && adduser -S jffast -G jffast

WORKDIR /app
# 通配而非写死 jffast-boot.jar：finalName 一旦带版本号，精确文件名会让 COPY 直接失败
COPY --from=builder /build/jffast-boot/target/jffast-boot*.jar app.jar

# 文件本地存储（jffast.file.storage.type=local）默认写 ./upload，非 root 用户需要写权限。
# ⚠️ 该目录必须挂卷持久化，否则容器重建后已上传文件全部丢失；
#    K8s 多副本场景请改用 minio / aliyun-oss / tencent-cos（见部署文档）。
RUN mkdir -p /app/upload && chown -R jffast:jffast /app
USER jffast

EXPOSE 7200

# 只保留 JVM 参数与监听端口；数据源 / Redis / RSA / 初始密码一律外部注入，
# 镜像内不写死任何库名或口令。
ENV JAVA_OPTS="-Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200" \
    SERVER_PORT=7200

# 健康检查：只探测端口连通。
# 不用 /jffast-api/actuator/health 是因为 dev profile 未暴露 actuator，会让 dev 容器永远 unhealthy；
# 端口通 = Tomcat 起来了，配合 depends_on 的 service_healthy 已足够编排依赖。
#
# ⚠ 本条与 deploy/docker-compose.yml 里的 healthcheck 是**两份、互不覆盖关系要看场景**：
#   · 容器单独 `docker run`（可 dev / test / prod 任意 profile）→ 走本条端口探测；
#   · `docker compose up`（默认 SPRING_PROFILES_ACTIVE=prod）→ 编排里那份 healthcheck 会
#     **覆盖**本条，走 /jffast-api/actuator/health；prod 已在 application-prod.yml 暴露
#     health,info，所以两份口径各自成立。
#   改 Spring profile 默认值或 actuator 暴露项时，这两处要一起看，否则会出现
#   「编排里 healthy、docker run 却 unhealthy」（或反之）的困惑。
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD nc -z localhost "${SERVER_PORT}" || exit 1

# fail-fast：未显式指定 Spring profile 时立即退出并给出可操作提示，
# 而不是以「找不到数据源」的姿态在启动阶段留下难以定位的堆栈。
ENTRYPOINT ["sh", "-c", "if [ -z \"$SPRING_PROFILES_ACTIVE\" ]; then echo '[FATAL] SPRING_PROFILES_ACTIVE is not set. Use -e SPRING_PROFILES_ACTIVE=dev|test|prod' >&2; exit 1; fi; exec java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -jar app.jar"]
