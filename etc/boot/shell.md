# etc/boot Shell 腳本說明

## run.sh
- 進入指定的應用程式目錄，載入 `.env` 環境設定與 Java 相關變數後，先根據 `application.pid` 優雅地停止舊程序。
- 重新組合 `JAVA_OPTS`、`BOOTJAR_LOCATION`、`BOOT_ARGS` 等參數，透過 `nohup $JAVA_HOME/bin/java ...` 背景啟動 Spring Boot 應用，並將輸出導向 `/dev/null`。

## stop.sh
- 進入指定的應用程式目錄後，讀取 `application.pid`，送出 `SIGTERM` 讓現有的應用程式正常關閉，並等待 PID 檔案清除。
- 主要用於單純停止服務而不重新啟動。

## switch.sh
- 依照傳入的 `APP_HOME`，列出 `prepared_build` 與 `backup/` 下可用的版本，使用者可選擇 `new`（立即套用 `prepared_build`）或歷史備份版本。
- 驗證選擇後，把對應版本的內容複製回 `APP_HOME`，達成程式版本的快速切換。

## backup.sh
- 從 `prepared_build` 目錄建立備份，版本號可自訂，未提供時以時間戳命名，並寫入 `version.txt` 方便追蹤。
- 備份完成後會保留最新三個版本，刪除更舊的目錄，最後列出仍可用的備份版本。

## test.sh
- 以傳入的 `API_URL` 輪詢 `curl <URL>/actuator`，最長嘗試 35 次（每 5 秒一次），確認服務已可回應後才視為啟動成功。
- 若超過嘗試次數仍無法取得成功回應，腳本會回報失敗並退出。

## flow-data/bkup.sh
- 建立以時間戳命名的 `history/<timestamp>` 目錄，將 `flow-data/xml` 中的所有檔案移入該目錄，保留一份歷史備份。
- 若 `xml` 目錄內沒有檔案則顯示提示並結束，避免建立空的備份。

## flow-data/readResult.sh
- 讀取傳入路徑下的 `result.txt` 檔案，取得執行結果字串。
- 若內容為 `Success` 即回傳 0 供 Jenkins 判定為成功，否則回傳 1 以標記失敗。

# etc/jenkins Jenkinsfile 說明

## deploy/Jenkinsfile
- 提供涵蓋環境、子專案、遠端操作、分析與打包等完整參數集，若未從 UI 觸發則於 pipeline 內自動建立參數表單。
- `Checkout Source` 會依 Jenkinsfile 來源決定是否重新抓取 Git 程式碼，並列出關鍵環境變數供除錯。
- `Assemble Artifact`、`Test Report`、`Code Analysis`、`OWASP Analysis`、`Sonar Analysis` 分別執行 Gradle 組建、單元測試、靜態掃描、依賴弱掃與 SonarQube，並收集/發佈報表與產物。
- `Put Files` 依環境與 include/exclude remotes 設定推送成品；後續 `Remote Start`/`Remote Stop` 依啟動模式（Apply New/Restart/Rollback）呼叫 Gradle 任務遠端操作服務。
- `post` 區段記錄負責人、結果並可串接 Slack，確保每次部署皆有審計訊息。

## publish/Jenkinsfile
- 針對發佈型專案（預設 `fsap-dispatcher-client`）建置，參數控制是否執行組建、測試、分析、Sonar 與是否僅發佈至 Maven Local。
- `Checkout Source` 與 deploy 流程類似，用於取得乾淨的程式碼與環境資訊。
- `Assemble Artifact`、`Test Report`、`Code Analysis`、`OWASP Analysis`、`Sonar Analysis` 的內容與 deploy 版相同，用於確保程式品質。
- `Release Artifact` 依 `only_publishToMavenLocal` 與 `is_offline` 決定呼叫 `publishToMavenLocal` 或 `publish`，完成本地或遠端倉庫發佈。
- `post` 區整理建置結果並保留日誌，可選擇推送通知。

## flow-data/Jenkinsfile
- 針對 flow-data 專案定義參數（環境、Gradle 子專案、同步目標主機、是否同步、離線與 JDK Tool），若非 UI 觸發則在 pipeline 內宣告預設值。
- `Checkout Source` 在需要時拉取 Git 程式碼並顯示環境資訊，確保同步腳本來源一致。
- `testAdmAvailable` 會在同步前跑 Gradle 任務確認目標 ADM 主機可用，避免後續流程失敗。
- `Sync` 執行實際資料同步任務；`BackUp` 在同步後觸發 flow-data 的 `bkup` 任務，把既有資料備份到歷史資料夾。
- `post` 區提供與其他 Jenkinsfile 一致的結果紀錄與通知鉤子。
