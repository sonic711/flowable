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

# etc/jenkins Jenkinsfile 說明

## deploy/Jenkinsfile
- 提供涵蓋環境、子專案、遠端操作、分析與打包等完整參數集，若未從 UI 觸發則於 pipeline 內自動建立參數表單。
- `Checkout Source` 會依 Jenkinsfile 來源決定是否重新抓取 Git 程式碼，並列出關鍵環境變數供除錯。
- `Assemble Artifact`、`Test Report`、`Code Analysis`、`OWASP Analysis`、`Sonar Analysis` 分別執行 Gradle 組建、單元測試、靜態掃描、依賴弱掃與 SonarQube，並收集/發佈報表與產物。
- `Put Files` 依環境與 include/exclude remotes 設定推送成品；後續 `Remote Start`/`Remote Stop` 依啟動模式（Apply New/Restart/Rollback）呼叫 Gradle 任務遠端操作服務。
- `post` 區段記錄負責人、結果並可串接 Slack，確保每次部署皆有審計訊息。
