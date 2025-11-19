# Flowable 平台程式與架構說明

本文件同步 `fsap/poc/flowable` 目前的程式碼（HEAD `996499cc`，develop 分支），以便快速理解模組拆分、關鍵元件、建置鏈與操作流程。原 `FLOWABLE_PLATFORM.md`
的產品願景與長期需求已併入第 11 節，方便集中維護。

## 1. 專案概覽

- **目標**：將 Flowable 7.2.0 的 Engine、REST、IDM 組件包裝成可控的 API 平台，整合既有的 BT 基礎設施（監控、Eureka、Bridge
  Dispatcher、Logging）。
- **架構**：Gradle multi-module 專案，核心模組為 `boot:flowable`（可執行 Jar）、`web`（HTTP/安全配件）、`flowable-process`
  （流程引擎與資料層）。
- **執行環境**：Java 21、Spring Boot 3.5.6、Undertow 容器、Oracle DB（預設透過 thin driver 連線）。
- **部署型態**：本機可 `bootRun`，Jenkins pipeline 可建置/掃描/遠端部署，`gradle/deploy` 內建 SSH 自動化（DEV server 初始設定）。

## 2. 技術棧與版本

| 類別         | 套件 / 版本                                              | 說明                                                                                         |
|------------|------------------------------------------------------|--------------------------------------------------------------------------------------------|
| 語言/框架      | Java 21、Spring Boot 3.5.6                            | `gradle/libs.versions.toml` 由 toolchain 鎖定 JDK，並使用 Spring Dependency Management 插件。        |
| BPM/工作流    | Flowable 7.2.0                                       | 透過 `flowable-spring-boot-starter` + REST/Actuator starter 整合。                              |
| Web Server | Undertow 2.3.19.Final                                | 於 `boot:flowable` 引入，並排除 Spring Boot 預設 Tomcat。                                            |
| API 文件      | Springdoc OpenAPI 2.8.14 + Swagger UI                 | `web` 模組新增 `libs.swagger`，自動產生 `/v3/api-docs` 與 `/swagger-ui/**` 以便檢視 Flowable REST。        |
| 日誌         | Log4j2 2.25.1 + 自訂 logging.gradle                    | 所有模組排除 `spring-boot-starter-logging`，集中於 `boot/flowable/src/main/resources/log4j2.yml` 管理。 |
| 企業依賴       | `com.bot:fsap-*`                                     | 由 `libs.bundles.blue.tech.bundle` 提供 Dispatcher Client、監控插件等。                              |
| 檢測掃描       | SpotBugs、Checkstyle、OWASP Dependency Check、SonarQube | 已於 root `build.gradle` 及 `gradle/*` 中預設。                                                   |

## 3. Git 狀態與近期提交

| Commit    | 日期         | 重點                                                       |
|-----------|------------|----------------------------------------------------------|
| `990faa0` | 2025-11-19 | 調整 Basic Auth + 驗證失敗 JSON 回應（`web` 模組 `SecurityConfig`）。 |
| `d7ea4bc` | 2025-11-19 | 調整 Undertow 相關設定 (`application-undertow.yml`)。           |
| `9b16b71` | 2025-11-19 | 導入 HTTP Request/Response logging filter。                 |
| `f048d5f` | 2025-11-19 | 專案改用 Undertow 以符合無阻塞需求。                                  |
| `7b6000d` | 2025-11-18 | SonarQube 設定調整。                                          |

> Jenkins pipeline 及 `gradle/version-info.gradle` 會將 Git 版本資訊寫入 `application-info.yml` 與 `git.properties`，方便
> Trace。

## 4. 目錄與模組速覽

```
flowable/
├── boot
│   ├── build.gradle            # 聚合子模組、設定 bootJar 名稱
│   └── flowable                # 可執行模組 (BootApplication + 資源)
├── flowable-process            # Flowable Engine/資料層
├── web                         # HTTP/安全/記錄
├── gradle                      # 共用 script：logging、deploy、scan、publish…
├── Jenkinsfile                 # CI/CD pipeline
└── README.md                   # 本文件（含原 FLOWABLE_PLATFORM.md backlog）
```

`settings.gradle` 指定 `include 'web', 'boot:flowable', 'flowable-process'`；根專案 jar/bootJar 關閉，僅子模組產出工件。

## 5. 建置鏈與自動化

### 5.1 Gradle 與版本管理

- `gradle/libs.versions.toml` 定義套件/插件版本與 bundle（Spring、Flowable、Undertow、BlueTech、logging 等）。
- 所有子專案自動套用：`java-library`、`org.springframework.boot`、`io.spring.dependency-management`、
  `com.gorylenko.git-properties`。
- root `build.gradle` 額外引入：
    - `gradle/logging/logging.gradle`：集中 log4j2/slf4j 依賴並排除 logback。
    - `gradle/dependency-check/*`、`gradle/spotbugs/*`、`gradle/checkstyle/*`：統一掃描規則。
    - `gradle/publish/*`、`gradle/version-info.gradle`：打包 metadata、版本資訊、`application-info.yml` token 取代。

### 5.2 Jenkins pipeline (`Jenkinsfile`)

- 互動參數：環境 (`env`)、要建置的 project、是否進行 assemble/test report/code analysis/OWASP/Sonar/put files/remote start
  等。
- Stages：
    1. `Apply Parameters`：根據 UI 或 pipeline 預設建立參數。
    2. `Checkout Source`：SCM checkout、列印環境變數。
    3. `Assemble Artifact`：`./gradlew ${project}:build -x test`（可選 offline）。
    4. `Test Report`：`./gradlew test` 並收集 `**/build/test-results`。
    5. `Code Analysis`：產出 Gradle report、Licence report、Checkstyle/SpotBugs HTML。
    6. `OWASP Analysis`、`Sonar Analysis`：條件式執行。
    7. `Put Files`：呼叫 `gradle/deploy/deploy.gradle`，上傳 `application*.yml`、`log4j2`、`flowable.jar` 至 remote。
    8. `Remote Start/Stop`：支援 `Apply_New_Version`、`Restart`、`Rollback` 模式。

### 5.3 Deploy Script (`gradle/deploy/deploy.gradle`)

- 使用 `org.hidetake.ssh` 插件；內建 DEV server 範例（`flowuser@172.17.24.79`）。
- 任務：
    - `sshInfo`：顯示 deploy 用私鑰路徑。
    - `put_files`：依環境將 `application.yml / adapter.yml / application-eureka.yml / log4j2.yml / bootJar` 置於遠端
      `app_home`；並維護 `.env`、`jmh.env` 內容。
    - `remote_start` / `remote_stop`：透過 `bin_home` 腳本起停服務，並保留 N 個版本。
- 相關屬性可由 `-Denv=<dev|sit|uat>`、`-Dinclude_remotes`、`-Dexclude_remotes` 控制。

## 6. 模組詳解

### 6.1 `boot:flowable`

- **啟動類別**：`com.bot.fsap.flowable.BootApplication` (`boot/flowable/src/main/java/.../BootApplication.java`) 以
  `@ComponentScan("com.bot")` 掃描其餘模組 Bean。
- **gradle 設定**：
    - `bootJar` 產出 `flowable.jar`，mainClass 設為 `com.bot.fsap.flowable.BootApplication`。
    - `processResources` 會依 `-Denv=<profile>` 或 `-Pprofile` 取代 `application*.yml` 中的 token（
      `@spring.active.profiles@` 等），並把對應 profile 的設定複製到輸出目錄。
    - `bootRun` 預設 JVM 參數 `-Xms512m -Xmx2048m`、`-Dlog4j.skipJansi=false`、`-DBASE_PATH=$HOME/app/fsap/log`、
      `-Dlogging.config=.../log4j2.yml`。
    - 子模組 `configurations` 排除 `spring-boot-starter-tomcat`，改由 Undertow (`libs.bundles.undertow`) 提供容器。
- **資源**：
    - `config/<profile>/application.yml`：指定資料庫、profiles include (`eureka, actuator, info, flowable, undertow`)、
      `server.port=8081`。
    - `config/<profile>/adapter.yml`：Bridge Dispatcher/nebula adapter 靜態 hosts 列表。
    - `config/<profile>/application-eureka.yml`：Eureka 註冊/心跳設定，metadata 包含 `gRPCPort`。
    - `application-actuator.yml`：暴露所有 Actuator endpoint、健康檢查細節。
    - `application-info.yml`：由 `processResources` + `gradle/version-info.gradle` 注入 build/githash。
    - `application-undertow.yml`：細緻控制 buffer、threads、max-post-size、access log 格式等。
    - `log4j2.yml`：定義 Console、Application、JMS、Log-agent、HTTP exchange 等 appender。
    - `META-INF/spring.factories`：暴露必要的 AutoConfiguration（沿用企業共用設定）。

### 6.2 `web`

- **定位**：不可獨立啟動（`bootRun=false`、`bootJar=false`），作為 `boot` 的 HTTP adapter。
- **安全機制**（`web/src/main/java/com/bot/fsap/flowable/web/security/SecurityConfig.java`）：
    - `@Order(1)` 的 `swaggerFilterChain` 以 `securityMatcher("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**",
      "/swagger-resources/**", "/webjars/**")` 放行所有 Swagger 資源，只停用 CSRF，並保留 Undertow 預設 session 行為，確保
      Swagger UI 載入時不會被 Stateless 限制擋下。
    - `@Order(2)` 的 `apiFilterChain` 負責 Flowable REST/IDM：關閉 CSRF/FormLogin、啟用 HTTP Basic、SessionPolicy 設為
      `STATELESS`，並維持結構化 JSON 的 `authenticationEntryPoint`（timestamp/status/message/path）。
- **API 文件**：`web/build.gradle` 引入 `libs.swagger`（`springdoc-openapi-starter-webmvc-ui` 2.8.14），可於
  `http://localhost:8081/swagger-ui/index.html` 檢視 `/flowable-rest/**` 模型。`swaggerFilterChain` 放行文件端點，其餘 API
  仍需 Basic Auth。
- **請求/回應紀錄**（`web/.../logging/RequestResponseLoggingFilter.java`）：
    - `OncePerRequestFilter` 包裝 `ContentCachingRequest/ResponseWrapper`，建立 `X-Request-Id`，並以 logger
      `HttpExchangeLogger` 列印 method/URI/status/耗時/headers/params/payload。
    - 敏感 header（Authorization/Cookie/X-API-Key 等）以 `***masked***` 取代，payload 超過 16KB 會截斷。
    - 對應 `log4j2.yml` 的 `HTTP-EXCHANGE` rolling file，方便稽核。

### 6.3 `flowable-process`

- **資料來源**（`DataSourceConfiguration`）：使用 HikariCP，連線池大小 50、最少閒置 5、Idle 600s、MaxLifetime 1800s，參數由
  `spring.datasource.*` 取得（可透過 JVM system properties 覆寫）。
- **流程引擎**（`ProcessEngineConfiguration`）：實作 `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>`，基於
  `application-flowable.yml` 注入 `database-schema-update`、`async-executor-activate`、`history-level`，並記錄 log。
- **流程部署**（`ProcessDeploymentConfiguration`）：
    - 只在 `dev`、`prod` profile 啟動（`@Profile`).
    - CommandLineRunner 啟動時掃描 `classpath:/processes/simple.bpmn20.xml`。
    - 若 `simpleProcess` 未部署則執行 `RepositoryService.createDeployment()`，完成後列出所有流程版本；在測試環境（偵測
      `org.springframework.test.context.TestContext`）不拋例外以避免測試失敗。
- **IDM 管理者**（`AdminUserInitializer`）：啟動後建立 `flowable.admin.users`（預設 `rest-admin, admin`）帳號，給予
  `access-admin/task/modeler/rest-api` 權限並以 `flowable.admin.default-pw`（預設 `test`）設密碼。
- **資源**：
    - `application-flowable.yml`：Flowable schema、async executor、history level、REST/IDM 開關、預設管理者清單。
    - `processes/simple.bpmn20.xml`：示範流程（開始→使用者任務→結束），附帶 form properties，方便驗證部署。

## 7. 組態檔一覽

| 檔案                                                                         | 內容重點                                                                                                     |
|----------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `boot/flowable/src/main/resources/config/<profile>/application.yml`        | Profiles include (`eureka,actuator,info,flowable,undertow`)、Oracle datasource、JPA 設定、`server.port=8081`。 |
| `boot/flowable/src/main/resources/config/<profile>/adapter.yml`            | BT Bridge Dispatcher/nebula adapter 參數及靜態 host 列表。                                                       |
| `boot/flowable/src/main/resources/config/<profile>/application-eureka.yml` | Eureka 註冊、心跳、metadata，將 `grpc.server.port` 透過 metadata 對外暴露。                                             |
| `boot/flowable/src/main/resources/application-actuator.yml`                | Actuator endpoint 曝光、健康檢查細節、管理權限。                                                                        |
| `boot/flowable/src/main/resources/application-info.yml`                    | 由 Token 置換輸出 build number、gitRevision、gitBranch、version 等。                                               |
| `boot/flowable/src/main/resources/application-undertow.yml`                | Buffer/Thread/Timeout/access log 設定，確保 Undertow tuning 可被 profile include。                               |
| `boot/flowable/src/main/resources/log4j2.yml`                              | Console/Application/JMS/Log-agent/HTTP exchange appender，`BASE_PATH` 預設 `/Users/sonic711/app/fsap/log`，可用 `-DBASE_PATH` 覆蓋。 |
| `flowable-process/src/main/resources/application-flowable.yml`             | Flowable schema、async executor、history level、REST/IDM、admin user/default pw。                             |
| `flowable-process/src/main/resources/processes/simple.bpmn20.xml`          | 範例 BPMN，提供表單欄位與中文說明。                                                                                     |

> `boot` 模組的 `processResources` 只會把 `application*.yml`、`META-INF`、`log4j2.yml` 等明確列出的檔案帶進 Jar；新增
> profile 時請同步更新 `sourceSets.main.resources` 的 include 設定。

## 8. Flowable 執行路徑與 REST 介面

1. 使用 `./gradlew :boot:flowable:bootRun -Denv=local` 或 `java -Denv=dev -jar boot/flowable/build/libs/flowable.jar`
   啟動。
2. Spring Boot 讀取 `application.yml`（依 env 決定 profile），再 include `info`、`flowable` 等子組態。
3. `web` 模組載入 Security Filter Chain 與 Request/Response Logging Filter。
4. `flowable-process` 建置 DataSource → ProcessEngine → Flowable REST (`/flowable-rest/**`) 與 IDM。
5. `AdminUserInitializer` 確保 `rest-admin/test` (及 `admin/test`) 存在，支援 Flowable REST Basic Auth。
6. `ProcessDeploymentConfiguration` (dev/prod) 自動部署 `simpleProcess` 並列出部署資訊。
7. Actuator `/actuator/**` 與 Eureka 註冊將由 `boot` 模組管理；log4j2 使用 `BASE_PATH` 分隔主機上的 log 目錄。

常用驗證指令：

```bash
# 啟動（local profile，覆寫 Oracle 連線）
./gradlew :boot:flowable:bootRun -Denv=local \
  -Dspring.datasource.url=jdbc:oracle:thin:@127.0.0.1:1521/ORCLCDB \
  -Dspring.datasource.username=FLOWTESTDB \
  -Dspring.datasource.password=flowtest123

# 查詢 Flowable 流程定義
curl -u rest-admin:test \
  http://localhost:8081/flowable-rest/service/repository/process-definitions

# Actuator 健康檢查
curl http://localhost:8081/actuator/health
```

- Swagger 文件：瀏覽 `http://localhost:8081/swagger-ui/index.html` 或 `/v3/api-docs`，其路徑由 `swaggerFilterChain` 放行，方便檢視 Flowable REST 模型，其餘 API 仍需 Basic Auth。

## 9. Logging、監控與觀察性

- **HTTP 交換 Log**：`RequestResponseLoggingFilter` 將資訊寫至 logger `HttpExchangeLogger`；`log4j2.yml` 的
  `HTTP-EXCHANGE` RollingFile 會以天/小時切割。
- **應用核心 Log**：`APPLICATION` appender 輸出 `${BASE_PATH}/${APPLICATION_NAME}_application.log`，保留 30 天，並支援
  `LOG4J2_DISABLE_ANSI` 控制彩色輸出；若未指定 `BASE_PATH`，預設使用 `/Users/sonic711/app/fsap/log`。
- **JMS/Log-agent**：對應 `JmsSysInfoLogger`、`LOG-AGENT` appender，符合 BT 監控 agent 需求。
- **Actuator**：`application-actuator.yml` 將所有 endpoint (`*`) 暴露，`health.show-details=ALWAYS`，方便監控平台收集資訊。
- **Eureka**：`application-eureka.yml` 預設 `registerWithEureka=false`，如需註冊請調整 `registerWithEureka`、`serviceUrl`
  並於 `processResources` include。metadata 中可附加 gRPC port 等客製資訊。

## 10. 建置 / 測試 / 發布流程

1. **初始化**：`./gradlew --version`（會下載 wrapper 相依）。
2. **檢查/建置**：
   ```bash
   ./gradlew clean build          # 子模組產出 jar，root 不產生工件
   ./gradlew :flowable-process:test --info   # 針對流程模組測試
   ```
3. **靜態檢查**（可在本機觸發與 Jenkins 相同報表）：
   ```bash
   ./gradlew check :projectReport :generateLicenseReport
   ./gradlew dependencyCheckAnalyze
   ./gradlew sonar --info -Dsonar.login=<token>
   ```
4. **封裝/部署**：
   ```bash
   ./gradlew :boot:flowable:bootJar
   ./gradlew -Denv=dev put_files        # 將 jar 與設定推到 DEV server
   ./gradlew -Denv=dev remote_start     # 遠端啟動 flowable.jar
   ```
5. **離線包**：Jenkins 參數 `is_offline=true` 會呼叫 `./gradlew --offline` 產出依賴清單。

## 11. 開發建議與後續工作

- `flowable-process` 目前僅部署單一範例流程，後續若要支援多 BPMN/DMN 建議將部署邏輯改為掃描
  `classpath*:/processes/**/*.bpmn20.xml`，並建立部署 Metadata 表。
- `SecurityConfig` 目前以雙 Security Filter Chain（Swagger 放行 + Flowable REST Basic Auth/Stateless）為基礎，若要串接企業 IAM
  仍需在 `web` 模組新增 OAuth2/JWT Filter 並調整兩條 chain 的授權範圍。
- 針對資料庫尚未加入 Flyway/Liquibase，若需要版本化 schema 請新增 migration 工具並於 build pipeline 中擴充步驟。
- 原 `FLOWABLE_PLATFORM.md` 的 API/監控/請求留存需求已納入本節，建議直接拆為 issue/backlog 管理。

---
最後更新：2025-11-19。若未來新增模組或 profile，請同步調整本文件與 `processResources` include 規則。
