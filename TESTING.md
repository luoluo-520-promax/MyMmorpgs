# 测试套件说明（TestNG）

## 约定

- 每个模块 `src/test/resources/` 下：
  - **`testng-unit.xml`**：单元测试（快速、默认）
  - **`testng-integration.xml`**：功能集成测试（可能依赖 Docker / 外部服务）
- 父工程属性 **`testng.suiteXmlFile`** 指向当前模块要执行的套件文件。

## Maven 命令

### 默认（全仓库单元测试）

```bash
mvn test
```

### 全仓库集成测试（需 Docker：`redis:7-alpine`，用于 battle-service、player-service）

```bash
mvn test -Pintegration-tests
```

### 仅某模块 + 指定套件文件

在模块目录下（`project.basedir` 为模块根目录时，可使用相对路径）：

```bash
cd battle-service
mvn test "-Dtestng.suiteXmlFile=src/test/resources/testng-integration.xml"
```

或在仓库根目录：

```bash
mvn test -pl battle-service "-Dtestng.suiteXmlFile=%cd%/battle-service/src/test/resources/testng-integration.xml"
```

（Linux/macOS 将 `%cd%` 换为 `$PWD` 或写绝对路径。）

### 仅某模块 + 集成 profile（等价于使用该模块的 `testng-integration.xml`）

```bash
mvn test -pl activity-service -Pintegration-tests
```

## 聚合套件（IDE / TestNG 直接运行）

仓库根目录 `test-suites/`：

| 文件 | 说明 |
|------|------|
| `testng-all-unit.xml` | 引用各模块单元套件 |
| `testng-all-integration.xml` | 引用各模块集成套件 |

## 环境建议

| 环境 | 推荐命令 |
|------|----------|
| 本地开发 / CI 快速门禁 | `mvn test` |
| 预发 / 合并前全量 | `mvn test -Pintegration-tests`（需 Docker） |
