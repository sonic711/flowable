# Flowable 平台架構說明

本文件同步專案 `fsap/poc/flowable` 目前的實作狀態，說明模組拆分、組態、開發與測試流程，供後續擴充 Flowable API 平台時參考。

## 專案結構
- Gradle Multi-Module (`settings.gradle`): `boot` (可執行應用)、`web` (HTTP 介面層)、`flowable-process` (Flowable 引擎與部署)。
- Java 21 / Spring Boot 3.5.5，依賴版本集中於 `gradle/libs.versions.toml`，統一由 Spring Dependency Management 控制。
- Flowable 版本 7.2.0，透過官方 Spring Boot Starter 建置流程引擎與 REST 能力。

### 模組職責
1. **boot**
    - 啟動類 `BootApplication`，掃描 `com.bot` 底下所有元件。
    - 聚合 `web` 與 `flowable-process`，並提供 Actuator、OpenAPI (`SpringDocConfig`) 以及對外設定檔 (`application*.yml`)。
    - 自訂 `bootJar`，輸出 `flowable.jar`，執行時可用 `-Denv=<profile>` 注入 active profile 與 logging 參數。
2. **web**
    - 提供 HTTP 請求處理的 Adapter，聚焦在多字符集支援：
        - `DynamicCharsetFilter` 解析 `Content-Type` 中的 `charset`，並設定請求/回應編碼。
        - `DynamicCharsetHttpMessageConverter` 與 `CharsetResponseBodyAdvice` 讓 Spring MVC 依據請求編碼輸出 JSON。
    - 範例 `TestController` 於 `/api/data`、`/api/user` 驗證 UTF-8/BIG5 JSON 流程。
    - 模組僅產生普通 JAR (`bootJar` disabled)，由 `boot` 匯入使用。
3. **flowable-process**
    - 封裝 Flowable 引擎設定與 BPMN 自動部署：
        - `ProcessEngineConfiguration`：覆寫 schema update、async executor、history level 等引擎行為。
        - `DataSourceConfiguration`：以 HikariCP 管理 H2 datasource，依 `dev/test/default` 與 `prod` profile 切換記憶體/檔案模式並提供 `PlatformTransactionManager`。
        - `ProcessDeploymentConfiguration`：在 `dev/prod` 啟動時自動部署 `classpath:/processes/simple.bpmn20.xml`，並列出已部署定義。
    - `application-flowable.yml` 設定 Flowable 專用參數（async executor、歷史層級、REST、部署 metadata 等）。

## Runtime 流程概觀
1. `boot` 啟動時讀取 `application.yml`，預設 profile `local`，並 include `eureka`、`actuator`。
2. Spring Context 透過 `@ComponentScan("com.bot")` 載入 `web` Filter/Controller 以及 Flowable 配置。
3. `flowable-process` 建立 Hikari DataSource (H2) 與 Flowable Engine，必要時自動部署 BPMN；`RepositoryService` 僅在該模組啟用 profile 時注入。
4. HTTP 請求先經 `DynamicCharsetFilter` 設定編碼，再由 MVC + Flowable REST（`/flowable-rest/**`）或範例 Controller 處理。
5. Actuator (`/actuator/**`) 與 SpringDoc (`/swagger-ui.html`, `/v3/api-docs`) 由 `boot` 模組提供監控/文件能力；Eureka 註冊行為可透過 `application-eureka.yml` 開關。

## 組態與環境
| 檔案 | 用途 |
|------|------|
| `boot/src/main/resources/application.yml` | 核心 Spring/資料庫/連線池設定，預設 H2 in-memory。 |
| `boot/src/main/resources/application-actuator.yml` | Actuator endpoint 與健康檢查權限。 |
| `boot/src/main/resources/application-eureka.yml` | 對 Eureka Server 的 client 設定 (註冊、心跳)。 |
| `boot/src/main/resources/log4j2.yml` | Log4j2 appenders，`bootRun` 時會複製到 target 並可經 JVM 參數指定輸出目錄。 |
| `flowable-process/src/main/resources/application-flowable.yml` | Flowable 引擎、async executor、部署、REST 與自訂 `blue.flowable` 命名空間。 |

> `boot` 模組的 `processResources` 會依 `env`/`profile` 參數將 `@spring.active.profiles@` 等 token 替換進 yml，確保 package/release 對應正確環境。

## 依賴與建置
- `./gradlew clean build`：建置所有模組；根目錄 `bootJar` / `jar` 被停用以避免重複打包。
- `./gradlew :boot:bootRun -Denv=local`：啟動服務（預設 8081，H2 console `/h2-console`）。
- `bootRun` JVM 參數預設 `-Xms512m/-Xmx2048m` 並指定 Log4j2 config、輸出路徑。
- 測試依賴集中於 `flowable-process`（JUnit 5、AssertJ、Testcontainers），目前尚未新增實際測試案例，可據此擴充。

## 請求編碼處理流程
1. 客戶端以 `Content-Type: application/json; charset=<encoding>` 呼叫 `/api/**` 或 Flowable REST。
2. `DynamicCharsetFilter` 解析 charset → 設置 `requestCharset` attribute。
3. `DynamicCharsetHttpMessageConverter` 依 attribute 產生對應編碼的 JSON；`CharsetResponseBodyAdvice` 確保 Response Header 帶上 `charset`。
4. `TestController` 可作為驗證工具，回傳 `requestCharset` 與 payload，快速確認 BIG5/UTF-8 是否被正確處理。

## Flowable 自動部署與資源
- 預設掃描 `flowable-process/src/main/resources/processes/`，目前包含 `simple.bpmn20.xml`。
- 若流程已部署 (`processDefinitionKey = simpleProcess`)，後續啟動會跳過重複部署，僅記錄現有版本。
- 可將自訂 BPMN/DMN 檔案放入相同目錄並調整 `blue.flowable.process-deployment` 參數控制是否自動部署。

## 開發與維護紀錄
- 2025-11-11：建立 Flowable API 平台原始需求與策略草稿。
- 2025-11-11：導入三模組架構、Flowable 7.2.0、自動部署與多字符集 Web 層。
- 2025-11-11：本文件更新為反映實際專案結構與執行方式。

## 後續建議
1. 擴增 Flowable REST/Runtime API 的 BFF 層，補齊流程定義、任務管理、變數查詢等對外介面。
2. 針對 `flowable-process` 撰寫整合測試（使用 Testcontainers + H2/HSQL）驗證 auto-deploy 與 async executor 行為。
3. 將請求留存/重播策略落實為 `web` 模組中的 Filter/Repository，並串接企業級儲存體。
