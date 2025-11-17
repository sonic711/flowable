# Flowable 平台程式與架構說明

本文件同步 `fsap/poc/flowable` 目前的實作，說明模組拆分、關鍵元件、配置與日常操作，供後續擴充 Flowable API 平台時參考。

## 技術棧與目標

- **語言/框架**：Java 21、Spring Boot 3.5.6、Flowable 7.2.0。
- **建置系統**：Gradle Multi-Module，藉由 `gradle/libs.versions.toml` 管理版本，並套用自訂 Log4j2 依賴組態 (
  `gradle/logging/logging.gradle`)。
- **資料庫**：預設採 Oracle (thin driver) 連線，使用 HikariCP 參數化連線池 (`DataSourceConfiguration1`)；若切換
  profile，可改由環境提供的 `spring.datasource.*`。
- **目標**：將 Flowable Engine + REST 服務包裝成可管控的 API 平台，同時在 Web 層處理多字符集請求、掛載企業監控套件並整合
  Actuator/Eureka。

## 資料夾與模組結構

| 位置                     | 說明                                                            |
|------------------------|---------------------------------------------------------------|
| `settings.gradle`      | 定義 multi-module (`boot`, `web`, `flowable-process`)。          |
| `boot/`                | 可執行模組，負責啟動 Spring Boot、整合 HTTP/Flowable 模組並輸出 `flowable.jar`。 |
| `web/`                 | 純 JAR，提供 HTTP Adapter（Filter/Security/Controller）與動態編碼處理。     |
| `flowable-process/`    | Flowable Engine、資料來源、流程部署與 IDM 帳號初始化邏輯。                       |
| `FLOWABLE_PLATFORM.md` | 產品願景與長期需求清單。                                                  |

## 模組詳解

### boot 模組 (`boot/build.gradle`, `BootApplication`)

- 啟動類 `com.bot.fsap.flowable.BootApplication` 以 `@ComponentScan("com.bot")` 聚合其餘模組。
- `processResources` 利用 Ant `ReplaceTokens` 依 `-Denv=<profile>` 或 `-Pprofile` 取代 `application*.yml` 中的
  `@spring.active.profiles@` 等 token，確保打包後的 profile 與版本資訊一致。
- `bootJar` 產出 `flowable.jar`，`bootRun` 預設 JVM Heap (512m/2048m) 並指定 Log4j2 設定路徑與輸出目錄 (
  `-DBASE_PATH=$HOME/app/fsap/log`)。
- 引入 `web`、`flowable-process` 以及企業監控套件 (`fsap-dispatcher-client`, `fsap-monitor-plugin`) 後由 Actuator (
  `application-actuator.yml`) 及 Eureka (`application-eureka.yml`) 暴露監控指標。

### web 模組

- **Security**：`SecurityConfig` 啟用 HTTP Basic、關閉 CSRF/FormLogin，Session Policy 設為 `ALWAYS` 以支援 Flowable REST
  既有的 Basic Auth 流程。
- **動態編碼處理鏈**：
    1. `DynamicCharsetFilter` 解析 `Content-Type` 中的 `charset`，驗證後設定到 `request`/`response` 以及 `requestCharset`
       attribute。
    2. `WebMvcConfig` 移除預設 `MappingJackson2HttpMessageConverter`，註冊自訂 `DynamicCharsetHttpMessageConverter`
       以相同編碼寫回 JSON。
    3. `CharsetResponseBodyAdvice` 最終補上 `Content-Type` 的 `charset` 參數，確保回應標頭正確。
- **HTTP 介面**：`TestController` 提供 `/api/data` 與 `/api/user` 兩個 POST 範例，可使用
  `Content-Type: application/json; charset=BIG5` 驗證整條管線。

### flowable-process 模組

- **資料來源**：`DataSourceConfiguration1` 直接使用 `spring.datasource.*` 參數建立 HikariDataSource，並設定連線池大小（最大
  50 / 最少閒置 5 / Idle 600s / MaxLifetime 1800s）。
- **引擎客製化**：`ProcessEngineConfiguration` 透過 `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` 將
  `flowable.process.database-schema-update`、`async-executor-activate`、`history-level` 等設定值（預設來自
  `application-flowable.yml`）注入引擎。
- **流程部署**：`ProcessDeploymentConfiguration` (啟用於 `dev`、`prod` profile) 啟動時掃描 `classpath:/processes/`，若
  `simpleProcess` 尚未部署則載入 `simple.bpmn20.xml`，並列印現有流程版本；RepositoryService 為可選注入以避免測試環境失敗。
- **IDM 管理者 bootstrap**：`AdminUserInitializer` (ApplicationRunner) 依 `flowable.admin.users` 列表建立帳號與權限（
  `access-admin/task/modeler/rest-api`），預設帳號 `rest-admin` / `admin` 密碼 `test`，用於 Flowable REST/IDM 登入。
- **Flowable Profile 組態**：`application-flowable.yml` 啟用 REST API (`rest-api-enabled=true`)、指定 schema `FLOWTESTDB`
  、啟用 async executor 與 full history，同時保留 LDAP 選項關閉。

## Runtime 與 HTTP 流程

1. 以 `./gradlew :boot:bootRun -Denv=local` 或 `java -Denv=dev -jar boot/build/libs/flowable.jar` 啟動後，
   `application.yml` 載入 profile `local`，並 include `eureka,actuator,flowable`，同時讀取 Oracle datasource 與 JPA 設定。
2. Spring 容器掃描 `com.bot` 套件：
    - `web` 模組註冊 Filter、MVC Config、Controller、Security Filter Chain。
    - `flowable-process` 建立 DataSource → ProcessEngine → Flowable REST (`/flowable-rest/**`) 與
      auto-deployment/ApplicationRunner。
3. 任何進入 `/api/**` 或 Flowable REST 的請求皆先通過 `DynamicCharsetFilter` 設定編碼；後續由 Spring MVC/Flowable
   共同處理，並透過自訂 Converter/Advice 以原編碼輸出。
4. Flowable REST 需以 HTTP Basic 登入（帳號由 `AdminUserInitializer` 建立），帳密亦可對應企業 IAM 之後續實作。
5. Actuator 端點 (`/actuator/**`) 與 Eureka 註冊由 `boot` 模組管理；Log4j2 設定檔 `boot/src/main/resources/log4j2.yml` 透過
   `-DBASE_PATH` 控制輪轉檔案位置。

## Flowable 流程資源與部署

- `flowable-process/src/main/resources/processes/simple.bpmn20.xml`：示範流程（開始 → 使用者任務 →
  結束），附帶表單欄位與註解，可用於驗證部署/啟動/任務指派。
- 若流程已部署，`ProcessDeploymentConfiguration` 會跳過重複部署但仍列出版本資訊；開發者可新增 BPMN/DMN 至 `processes/`
  ，或改寫該類判斷以支援多檔案批次部署。

## 組態檔一覽

| 檔案                                                             | 內容重點                                                                                                  |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `boot/src/main/resources/application.yml`                      | Spring 應用名稱、profiles include、Oracle datasource、JPA、新增 `server.port=8081` 並關閉全域編碼 Filter（交由自訂 Filter）。 |
| `boot/src/main/resources/application-actuator.yml`             | Actuator 全量暴露、健康檢查詳情、管理端點權限。                                                                          |
| `boot/src/main/resources/application-eureka.yml`               | Eureka client 開關、Server 列表、心跳設定與 metadata。                                                            |
| `boot/src/main/resources/log4j2.yml`                           | Console + RollingFile + Log-Agent Appender，透過 `BASE_PATH/APPLICATION_NAME` 決定輸出位置並自訂多組 logger level。  |
| `flowable-process/src/main/resources/application-flowable.yml` | Flowable schema、async executor、history、IDM/REST/管理者帳號等設定。                                             |

> 注意：`boot` 模組 `processResources` 只會把 `application*.yml` 打進可執行 JAR，其餘資源由各子模組提供；若需新增
> profile，請於 `processResources` 的 `filesMatching` 與 `sourceSets` 保持一致。

## 建置與啟動流程

1. **安裝依賴**：專案使用 Gradle Wrapper，直接執行 `./gradlew --version` 以產生 `.gradle`。
2. **編譯/測試**：`./gradlew clean build`（根模組 jar 關閉，子模組 `web/flowable-process` 仍輸出普通 JAR 供 `boot` 依賴）。
3. **本機啟動**：
   ```bash
   ./gradlew :boot:bootRun -Denv=local \
     -Dspring.datasource.url=jdbc:oracle:thin:@127.0.0.1:1521/ORCLCDB \
     -Dspring.datasource.username=FLOWTESTDB \
     -Dspring.datasource.password=flowtest123
   ```
   或使用 jar：`java -Denv=dev -jar boot/build/libs/flowable.jar`。
4. **驗證服務**：
    - Flowable REST：`curl -u rest-admin:
      test http://localhost:8081/flowable-rest/service/repository/process-definitions`。
    - 多編碼測試：
      ```bash
      curl -X POST http://localhost:8081/api/data \
        -H 'Content-Type: application/json; charset=BIG5' \
        -d '{"message":"測試訊息"}'
      ```
      回應會顯示 `requestCharset=Big5`。

## 開發/測試建議

- 針對 `flowable-process` 補齊整合測試，可利用 Testcontainers 啟動 Oracle/H2，驗證 DataSource 與自動部署/IDM bootstrap。
- 建立 Flowable REST BFF 層或 API Gateway，統一封裝 `/flowable-rest/**` 並加入請求留存、遮罩、節流等企業需求。
- 將 `web` 模組的 Filter/Converter 擴充為可設定允許的 charset 清單與 fallback 策略，並考慮對 Request/Response 進行審計。
- 若要導入更多 Flowable 資源 (DMN/CMMN/表單)，建議將 `ProcessDeploymentConfiguration` 的部署來源改為
  `classpath*:/processes/**/*.bpmn20.xml` 並加入 metadata 管理。

---
最後更新：2025-11-17，對應 commit 狀態同於當前程式碼。
