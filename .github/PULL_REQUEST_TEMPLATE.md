## 变更类型

- [ ] 新功能 `feat`
- [ ] 缺陷修复 `fix`
- [ ] 文档 `docs`
- [ ] 重构 `refactor`
- [ ] 性能优化 `perf`
- [ ] 其他

## 描述

<!-- 这个 PR 做了什么，为什么要做 -->

## 影响范围

<!-- 涉及哪些模块？是否改变现有行为？是否需要数据迁移或配置调整？ -->

## 自检清单

- [ ] 本地 `mvn test` 通过（CI 另跑 `mvn verify`，含 JaCoCo 覆盖率门禁）
- [ ] 提交信息符合 `type(scope): description` 规范
- [ ] 所有提交已按 DCO 签署（`git commit -s`，含 `Signed-off-by` 行）
- [ ] 遵循 [docs/开发规范.md](../docs/开发规范.md)
- [ ] 表结构变更已同步 `sql/` 下脚本，并在上方说明是否需要迁移
- [ ] 新增配置项已补充到 README 的配置说明中
