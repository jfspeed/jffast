# 贡献指南

感谢您对疾风（jffast）项目的关注！我们欢迎各种形式的贡献。

## 如何贡献

### 报告 Bug

如果您发现了 Bug，请通过 [GitHub Issues](https://github.com/summerZstar/jffast/issues) 报告，并包含以下信息：

1. **环境信息**
   - JDK 版本
   - Spring Boot 版本
   - 操作系统
   - 数据库版本

2. **复现步骤**
   - 清晰的步骤描述
   - 相关配置

3. **期望行为与实际行为**

4. **日志信息**
   - 相关的错误日志（请脱敏）

### 提交建议

功能建议同样通过 [GitHub Issues](https://github.com/summerZstar/jffast/issues) 提交，请说明：

- 建议的功能描述
- 使用场景
- 期望的实现方式

### 提交代码

#### 1. Fork 项目

```bash
# Fork 项目到你的 GitHub 账户
# Clone 你的 Fork
git clone https://github.com/your-username/jffast.git
cd jffast
```

#### 2. 创建分支

```bash
# 从 master 分支创建特性分支（仓库默认分支是 master，CI 监听 master / main）
git checkout -b feature/your-feature-name

# 或修复分支
git checkout -b fix/your-fix-name
```

#### 3. 开发规范

请遵循项目的开发规范，详见 [docs/开发规范.md](docs/开发规范.md)。

**代码风格：**

- 遵循 Java 编码规范
- 使用 4 空格缩进
- 类和方法必须有 Javadoc 注释
- 使用 Lombok 简化代码

**提交信息：**

采用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**类型（type）：**

- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响代码运行的变动）
- `refactor`: 重构（既不是新增功能，也不是修改 bug 的代码变动）
- `perf`: 性能优化
- `test`: 增加测试
- `chore`: 构建过程或辅助工具的变动

**示例：**

```bash
git commit -m "feat(system): 新增用户导出功能"
git commit -m "fix(auth): 修复登录超时问题"
git commit -m "docs: 更新部署文档"
```

#### 4. 测试

```bash
# 运行单元测试
mvn test

# 运行指定模块测试
# -am（also-make）不可省：全新 clone 时兄弟模块还没进本地仓库，
# 少了它 Maven 解析不到依赖模块而直接失败。本机跑过完整 install 时反而看不出问题。
mvn test -pl jffast-common,jffast-biz/jffast-system -am

# 编译检查
mvn compile -DskipTests
```

#### 5. 提交 PR

1. 确保代码通过所有测试
2. 更新相关文档
3. 推送到你的 Fork：
   ```bash
   git push origin feature/your-feature-name
   ```
4. 创建 Pull Request，填写：
   - 标题：简洁描述改动
   - 详细说明：改动内容、原因、测试情况
   - 关联 Issue（如有）

## 开发环境

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 21+ |
| Maven | 3.9+ |
| MySQL | 8.0+ |
| Redis | 7.0+ |
| Node.js | 22.22.1+（前端 jffast-ui 的 package.json 声明 `^22.22.1 \|\| ^24.0.0 \|\| >=26.0.0`，且 .npmrc 设了 engine-strict=true——版本不满足时 npm install 直接失败） |

### 本地开发

```bash
# 1. 克隆项目
git clone https://github.com/your-username/jffast.git
cd jffast

# 2. 初始化数据库
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/system_sql.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/system_init_data.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/flowable_schema/flowable_mysql_all.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/flow_sql.sql

# 3. 编译后端
mvn clean install -DskipTests

# 4. 启动后端
cd jffast-boot
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 5. 启动前端（可选，需另 clone jffast-ui 到同级目录）
cd ../jffast-ui
npm install
npm run dev
```

### Docker 开发

```bash
# 启动 MySQL + Redis
docker compose up -d

# 导入 SQL
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/system_sql.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/system_init_data.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/flowable_schema/flowable_mysql_all.sql
mysql -h 127.0.0.1 -uroot -ptest1234 jffast < sql/flow_sql.sql
```

## 项目结构

```
jffast/
├── jffast-bom/              版本管理
├── jffast-common/           基础层（BaseEntity/R/异常/工具）
├── jffast-starters/         Starter 聚合
│   ├── jffast-starter-mybatis/    数据库
│   ├── jffast-starter-web/        Web
│   ├── jffast-starter-security/   安全
│   ├── jffast-starter-redis/      缓存
│   ├── jffast-starter-log/        日志
│   ├── jffast-starter-file/       文件存储
│   ├── jffast-starter-excel/      Excel
│   ├── jffast-starter-job/        定时任务
│   ├── jffast-starter-flowable/   工作流
│   ├── jffast-starter-social/     社交登录
│   ├── jffast-starter-oauth2-client/  OAuth2 客户端
│   └── jffast-starter-demo-mode/  演示模式（只读保护，默认关闭）
├── jffast-biz/              业务模块
│   ├── jffast-system/       系统管理
│   ├── jffast-flow/         工作流业务
│   ├── jffast-ai/           AI 会话 / 知识库
│   └── jffast-demo/         示例模块
├── jffast-boot/             可运行应用
├── sql/                     SQL 脚本
└── docs/                    文档
```

## 提交检查清单

- [ ] 代码遵循项目编码规范
- [ ] 已添加必要的单元测试
- [ ] 所有测试通过（`mvn test`）
- [ ] 代码编译无警告（`mvn compile`）
- [ ] 已更新相关文档
- [ ] 提交信息符合 Conventional Commits 规范
- [ ] 不包含敏感信息（密码、密钥等）

## 代码审查

所有 PR 都需要经过代码审查。审查者会关注：

1. **代码质量**
   - 命名规范
   - 代码可读性
   - 重复代码

2. **功能正确性**
   - 功能是否完整
   - 边界情况处理
   - 异常处理

3. **性能**
   - 是否有性能问题
   - SQL 优化

4. **安全性**
   - 是否有安全漏洞
   - 敏感信息处理

## 行为准则

- 尊重每一位贡献者
- 接受建设性的批评
- 专注于对社区最有利的事情
- 对其他社区成员表示同理心

## 获取帮助

- **文档**：[README](README.md) 与 [docs/ 目录](https://github.com/summerZstar/jffast/tree/master/docs)（独立文档站尚未上线）
- **Issues**：[GitHub Issues](https://github.com/summerZstar/jffast/issues)
- **讨论**：[GitHub Discussions](https://github.com/summerZstar/jffast/discussions)

## 致谢

感谢所有为疾风项目做出贡献的人！