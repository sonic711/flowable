# Flowable REST API 原則介紹

- 開發環境測試用：[Swagger](http://172.17.24.79:8081/swagger-ui/index.html?urls.primaryName=Flowable-Identity#/user-resource/getUser)
- 使用說明請參考：[README.md 11.Swagger 頁面使用說明](README.md)

## 認證

Flowable REST API，Flowable Engine 會連接至Oracle資料庫。
REST API 採用 JSON 格式與 Spring MVC 架構。

### 認證原則

- 所有 REST 資源預設要求已授權使用者並具備 rest-access-api 權限。
- 預設管理帳號設定方法如下（於 [application-flowable.yml](flowable-process/src/main/resources/application-flowable.yml)
  檔）：
  ```
  flowable:
      admin:
        users: rest-admin,admin
        default-pw: test
  ```
  系統啟動時，若此帳號尚不存在則自動建立；已存在則直接使用。此帳號會被賦予 access-rest-api 權限。建議啟動後務必修改密碼。
- 若移除 flowable.rest.app.admin.user-id 設定，帳號及相關權限不會直接被移除。

### 基本 HTTP 認證

- 使用 Basic HTTP 認證，請於請求 HTTP-header 中包含 Authorization: Basic xxxx，或於 URL 中附帶帳號密碼 (
  `http://username:password@localhost:8080/xyz`)。
- 使用程式部署流程範例：

```java
import java.net.http.HttpHeaders;

RestTemplate restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory());

// ---------- Basic Auth ----------
String auth = USERNAME + ":" + PASSWORD;
String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.MULTIPART_FORM_DATA);
headers.add("Authorization","Basic "+encodedAuth);

// ---------- 讀取本地檔案 ----------
File bpmnFile = new File(BPMN_FILE_PATH);
if(!bpmnFile.exists() ||!bpmnFile.isFile()){
    throw new IllegalStateException("找不到 BPMN 檔案或不是檔案: "+BPMN_FILE_PATH);
}

FileSystemResource fileResource = new FileSystemResource(bpmnFile);

// ---------- 建立 multipart body（key 必須是 "file"） ----------
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file",fileResource);
HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
// ---------- 發送 POST ----------
try {
    ResponseEntity<String> response = restTemplate.exchange(FLOWABLE_URL, HttpMethod.POST, requestEntity, String.class);
    System.out.println("部署成功！HTTP 狀態碼: " + response.getStatusCodeValue());
    System.out.println("回應內容:\n" + response.getBody());
} catch(Exception e) {
	System.err.println("部署失敗: " + e.getMessage());
	e.printStackTrace();
    }
}
```

## 配置說明

- 建議所有 JSON POST/PUT 操作，HTTP Header 設定 Accept 與 Content-Type 為 application/json。

## Methods & Return Codes

| 方法     | 操作說明                        |
|--------|-----------------------------|
| GET    | 取得單一或多筆 resource            |
| POST   | 建立新 resource；或（當查詢結構複雜）執行查詢 |
| PUT    | 更新現有 resource；或執行資源相關動作     |
| DELETE | 刪除既有的 resource              |

### 主要回應碼解析

| 回應碼 | 說明                               |
|-----|----------------------------------|
| 200 | 操作成功，回傳結果（用於 GET、PUT）            |
| 201 | 操作成功，新 實體 已建立並回傳（用於 POST）        |
| 204 | 操作成功，實體 已刪除，不回傳內容（用於 DELETE）     |
| 401 | 未授權，需提供認證資訊或權限不足                 |
| 403 | 禁止操作，具授權但不允許此操作（如：刪除處於執行中流程的任務）  |
| 404 | 找不到 資源                           |
| 405 | 方法不允許，對該資源不可執行此方法（如不能 PUT 部署檔資源） |
| 409 | 衝突，修改資源時發生資源同時被修改、ID已存在等         |
| 415 | 媒體類型不支援、JSON 格式錯誤                |
| 500 | 伺服器內部錯誤，回應 body 會有詳細錯誤描述         |

## 錯誤回應範例

若出現錯誤（包含 4XX、5XX 狀態碼），回應會包含錯誤對象，例如：

```json
{
  "statusCode": 404,
  "errorMessage": "Could not find a task with id '444'."
}
```

若無法通過認證，回應如下：

```json
{
  "timestamp": "${當下時間}",
  "statusCode": 401,
  "errorMessage": "Unauthorized，需要認證才能存取此資源",
  "path": "${訪問的url}"
}
```

## 其他說明

- HTTP 回應內容型態：預設為 application/json，若請求二進位內容（例部署檔下載），則依實際內容型態。

## 請求參數 Request parameters

- URL 路徑片段（URL fragments）
  屬於 URL 路徑一部分的參數（例如 http://host/flowable-rest/process-api/repository/deployments/{deploymentId} 中的
  deploymentId 參數），若該路徑片段含有特殊字元，必須進行適當的 URL 編碼（URL-encoding 或
  Percent-encoding）。大多數主流框架已內建此功能，但開發時仍需特別留意。
  特別是當路徑片段可能包含正斜線（forward-slashes）時（例如部署資源的路徑），此編碼強制要求，否則會導致路由解析失敗或被誤解為目錄分隔。

- REST URL 查詢參數（Rest URL query parameters）
  以查詢字串（query-string）方式附加在 URL
  後面的參數（例如 http://host/flowable-rest/process-api/repository/deployments?name=Deployment 中的 name
  參數），支援下列類型，並會在對應的 Flowable REST-API 文件中明確標示：

### URL Query Parameters（網址列 ? 後面的參數）

這些參數直接附加在 URL 的 query string 中，例如：  
`http://host/flowable-rest/process-api/repository/deployments?name=Deployment`

| 類型          | 格式說明                                                          | 範例 / 注意事項                                                                            |
|-------------|---------------------------------------------------------------|--------------------------------------------------------------------------------------|
| **String**  | 純文字，可包含 URL 允許的任何有效字元                                         | `name=Simple process`                                                                |
| **XXXLike** | 支援 Like 模糊查詢時，需使用 `%` 作為萬用字元（在 URL 中需 URL encode 成 `%25`）     | `nameLike=Tas%25` → 匹配以 "Tas" 開頭的所有值<br>`nameLike=%25flowable%25` → 包含 "flowable" 的值 |
| **Integer** | 整數，範圍：-2,147,483,648 ~ 2,147,483,647                          | `size=50`                                                                            |
| **Long**    | 長整數，範圍：-9,223,372,036,854,775,808 ~ 9,223,372,036,854,775,807 | `processDefinitionId=123456789012345`                                                |
| **Boolean** | 僅接受 `true` 或 `false`（大小寫皆可）                                   | `active=true` <br>其他值會回傳 **400 Bad Request**                                         |
| **Date**    | 必須使用 **ISO-8601** 格式，包含日期與時間，並以 `Z` 表示 UTC 時間                 | `2013-04-03T23:45:00Z` <br>`2025-11-28T00:00:00Z`                                    |

### JSON Body Parameters（POST/PUT 時放在 request body 的 JSON）

| 類型          | 格式說明                             | 範例 / 注意事項                                                        |
|-------------|----------------------------------|------------------------------------------------------------------|
| **String**  | 純文字                              | `"name": "Simple process"`                                       |
| **XXXLike** | Like 查詢時直接使用 `%`（不需要 URL encode） | `"nameLike": "Tas%"` → 開頭匹配<br>`"nameLike": "%flowable%"` → 包含匹配 |
| **Integer** | JSON number，範圍同上                 | `"size": 50`                                                     |
| **Long**    | JSON number，範圍同上                 | `"processDefinitionId": 123456789012345`                         |
| **Date**    | 必須使用 ISO-8601 字串格式               | `"dueDate": "2013-04-03T23:45:00Z"`                              |

### Paging & Sorting 參數（僅適用於 URL Query String）

| 參數      | 預設值           | 說明                                 | 範例                                         |
|---------|---------------|------------------------------------|--------------------------------------------|
| `sort`  | 各 endpoint 不同 | 排序欄位名稱（每個 API 允許的欄位不同，請參考該 API 文件） | `sort=name` <br>`sort=startTime`           |
| `order` | `asc`         | 排序方向：`asc`（遞增）或 `desc`（遞減）         | `order=desc`                               |
| `start` | `0`           | 分頁偏移量（從第幾筆開始），**不是頁數**             | `start=0`（第一頁）<br>`start=20`（第三頁，若每頁 10 筆） |
| `size`  | `10`          | 每頁筆數                               | `size=25`                                  |

### 分頁範例（每頁 3 筆，共取 9 筆任務）

```http
GET /runtime/tasks?start=0&size=3
GET /runtime/tasks?start=3&size=3
GET /runtime/tasks?start=6&size=3
```
