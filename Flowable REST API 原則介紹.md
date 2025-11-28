# Flowable REST API 原則介紹

- 開發環境測試用：[Swagger](http://172.17.24.79:8081/swagger-ui/index.html?urls.primaryName=Flowable-Identity#/user-resource/getUser)
- 使用說明請參考：[README.md 11.Swagger 頁面使用說明](README.md)

## API 分類快速導覽

- [部署資源（Deployments）](#deployments-api)
- [流程定義（Process-definition）](#process-definition-api)
- [流程實例（Process-instance）](#process-instance-api)
- [執行（Execution）](#execution-api)
- [任務（Task）](#task-api)
- [歷史資料（History）](#history-api)
- [表單（Forms）](#forms-api)
- [管理（Management）與 Runtime](#management-runtime-api)
- [身分管理（Identity）](#identity-api)

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

### 分頁範例
start 參數會用作查詢的位移量。例如，若要取得三頁的任務，每頁三項 （共9 項），我們將使用：
```http
GET /runtime/tasks?start=0&size=3
GET /runtime/tasks?start=3&size=3
GET /runtime/tasks?start=6&size=3
```

## JSON 查詢變數格式
```json
{
  "name" : "variableName",
  "value" : "variableValue",
  "operation" : "equals",
  "type" : "string"
}
```
### 變數 JSON 屬性

| 屬性（Attribute） | 是否必填（Required） | 說明（Description）                                                                                                 |
|---------------|----------------|-----------------------------------------------------------------------------------------------------------------|
| name          | Yes            | 變數的名稱。                                                                                                          |
| value         | No             | 變數的值。若寫入變數時省略此欄位，將會使用 `null` 作為值。                                                                               |
| valueUrl      | No             | 僅在讀取 `binary` 或 `serializable` 類型的變數時出現，指向可下載原始二進位資料的 URL。                                                      |
| type          | No             | 變數的類型。請參考下方「變數類型」表格。<br>**強烈建議永遠明確指定 type**，若省略則系統會根據 JSON 原始類型自動推斷（僅限 string、double、integer、boolean），容易發生類型誤判。 |

### 內建變數類型（Variable Types）

| 類型名稱（Type name） | 對應 Java 類別          | 說明                                                             |
|-----------------|---------------------|----------------------------------------------------------------|
| string          | `java.lang.String`  | 直接使用 JSON 文字值。                                                 |
| integer         | `java.lang.Integer` | 優先使用 JSON number，無則 fallback 到 JSON text 轉換。                   |
| short           | `java.lang.Short`   | 同上。                                                            |
| long            | `java.lang.Long`    | 同上。                                                            |
| double          | `java.lang.Double`  | 同上。                                                            |
| boolean         | `java.lang.Boolean` | 使用 JSON boolean 值轉換。                                           |
| date            | `java.util.Date`    | 必須使用 ISO-8601 格式的文字（例如 `"2025-11-28T10:00:00.000+08:00"`）進行轉換。 |

> 除了以上內建類型外，Flowable 還支援 `binary` 與 `serializable` 兩種特殊類型（讀取時會回傳 `valueUrl`）。

<a id="deployments-api"></a>
## 部署資源 API

### Tomcat 使用提醒

- 若部署在 Tomcat，請先閱讀官方文件〈Usage in Tomcat〉以瞭解必要的設定步驟與注意事項。

### 查詢部署清單

`GET repository/deployments`

#### 查詢參數

| 參數                | 必填 | 類型      | 說明                                                                        |
|-------------------|----|---------|---------------------------------------------------------------------------|
| name              | 否  | String  | 僅回傳名稱等於指定值的部署。                                                            |
| nameLike          | 否  | String  | 僅回傳名稱與指定值類似的部署。                                                           |
| category          | 否  | String  | 僅回傳指定類別的部署。                                                               |
| categoryNotEquals | 否  | String  | 僅回傳不屬於指定類別的部署。                                                            |
| tenantId          | 否  | String  | 僅回傳指定 tenantId 的部署。                                                       |
| tenantIdLike      | 否  | String  | 僅回傳 tenantId 類似指定值的部署。                                                    |
| withoutTenantId   | 否  | Boolean | 為 true 時只回傳未設定 tenantId 的部署；false 則忽略此參數。                                 |
| sort              | 否  | String  | 排序欄位，可用 `'id'(預設)`、`'name'`、`'deployTime'`、`'tenantId'`，需搭配 `order` 一起使用。 |

#### 回應碼

| 代碼  | 說明    |
|-----|-------|
| 200 | 請求成功。 |

#### 成功回應範例

```json
{
  "data": [
    {
      "id": "10",
      "name": "flowable-examples.bar",
      "deploymentTime": "2010-10-13T14:54:26.750+02:00",
      "category": "examples",
      "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10",
      "tenantId": null
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "id",
  "order": "asc",
  "size": 1
}
```

### 取得單一部署

`GET repository/deployments/{deploymentId}`

#### URL 參數

| 參數           | 必填 | 類型     | 說明         |
|--------------|----|--------|------------|
| deploymentId | 是  | String | 想取得的部署 ID。 |

#### 回應碼

| 代碼  | 說明         |
|-----|------------|
| 200 | 找到並回傳部署資訊。 |
| 404 | 找不到指定部署。   |

#### 成功回應範例

```json
{
  "id": "10",
  "name": "flowable-examples.bar",
  "deploymentTime": "2010-10-13T14:54:26.750+02:00",
  "category": "examples",
  "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10",
  "tenantId": null
}
```

### 建立新部署

`POST repository/deployments`

#### Request Body 說明

- 內容型態須為 `multipart/form-data`。
- 請求中必須只有一個檔案，其欄位名稱即會成為部署名稱，其餘檔案會被忽略。
- 若需一次部署多個資源，請先壓縮成 `.bar` 或 `.zip` 檔並上傳。
- 可額外加入 `tenantId` form-field，值會成為該部署所屬的 tenant ID。

#### 回應碼

| 代碼  | 說明                             |
|-----|--------------------------------|
| 201 | 部署建立成功。                        |
| 400 | 請求沒有內容或內容型態不支援部署，詳細資訊請見回應狀態描述。 |

#### 成功回應範例

```json
{
  "id": "10",
  "name": "flowable-examples.bar",
  "deploymentTime": "2010-10-13T14:54:26.750+02:00",
  "category": null,
  "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10",
  "tenantId": "myTenant"
}
```

### 刪除部署

`DELETE repository/deployments/{deploymentId}`

#### URL 參數

| 參數           | 必填 | 類型     | 說明         |
|--------------|----|--------|------------|
| deploymentId | 是  | String | 要刪除的部署 ID。 |

#### 回應碼

| 代碼  | 說明           |
|-----|--------------|
| 204 | 部署已刪除，無回應內容。 |
| 404 | 找不到指定部署。     |

### 取得部署中的資源

`GET repository/deployments/{deploymentId}/resources`

#### URL 參數

| 參數           | 必填 | 類型     | 說明             |
|--------------|----|--------|----------------|
| deploymentId | 是  | String | 想檢視資源列表的部署 ID。 |

#### 回應碼

| 代碼  | 說明           |
|-----|--------------|
| 200 | 找到部署並回傳資源清單。 |
| 404 | 找不到指定部署。     |

#### 成功回應範例

```json
[
  {
    "id": "diagrams/my-process.bpmn20.xml",
    "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resources/diagrams%2Fmy-process.bpmn20.xml",
    "dataUrl": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resourcedata/diagrams%2Fmy-process.bpmn20.xml",
    "mediaType": "text/xml",
    "type": "processDefinition"
  },
  {
    "id": "image.png",
    "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resources/image.png",
    "dataUrl": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resourcedata/image.png",
    "mediaType": "image/png",
    "type": "resource"
  }
]
```

- `mediaType`：資源的媒體型態，由 pluggable 的 MediaTypeResolver 判斷，預設僅提供部分 mime-type 對應。
- `type`：資源類型，可為 `resource`（一般資源）、`processDefinition`（包含流程定義，部署器會處理）或 `processImage`（流程圖影像資源）。
- `dataUrl`：指向該資源二進位內容的 URL，可用於實際下載。

### 取得部署資源

`GET repository/deployments/{deploymentId}/resources/{resourceId}`

#### URL 參數

| 參數           | 必填 | 類型     | 說明                                                                    |
|--------------|----|--------|-----------------------------------------------------------------------|
| deploymentId | 是  | String | 資源所在部署的 ID。                                                           |
| resourceId   | 是  | String | 想取得的資源 ID，若包含斜線請務必先 URL encode，例如 `diagrams%2Fmy-process.bpmn20.xml`。 |

#### 回應碼

| 代碼  | 說明                         |
|-----|----------------------------|
| 200 | 找到部署與資源並回傳詳細資訊。            |
| 404 | 找不到部署或部署中沒有指定資源，詳細訊息會說明原因。 |

#### 成功回應範例

```json
{
  "id": "diagrams/my-process.bpmn20.xml",
  "url": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resources/diagrams%2Fmy-process.bpmn20.xml",
  "dataUrl": "http://localhost:8081/flowable-rest/service/repository/deployments/10/resourcedata/diagrams%2Fmy-process.bpmn20.xml",
  "mediaType": "text/xml",
  "type": "processDefinition"
}
```

- `mediaType` 及 `type` 說明同前段。

### 取得部署資源內容

`GET repository/deployments/{deploymentId}/resourcedata/{resourceId}`

#### URL 參數

| 參數           | 必填 | 類型     | 說明                            |
|--------------|----|--------|-------------------------------|
| deploymentId | 是  | String | 資源所在部署的 ID。                   |
| resourceId   | 是  | String | 想下載的資源 ID，若包含斜線請先 URL encode。 |

#### 回應碼

| 代碼  | 說明                         |
|-----|----------------------------|
| 200 | 找到部署及資源並回傳二進位內容。           |
| 404 | 找不到部署或部署無指定資源，狀態描述會提供更多資訊。 |

#### 成功回應說明

- 回應主體會是該資源的二進位內容，`Content-Type` 與資源的 `mimeType` 相同。
- 會同時設定 `Content-Disposition` header，以便瀏覽器直接下載檔案。

<a id="process-definition-api"></a>
## 流程定義 API

### 查詢流程定義清單

`GET repository/process-definitions`

#### 查詢參數

| 參數                | 必填 | 類型      | 說明                                                                                         |
|-------------------|----|---------|--------------------------------------------------------------------------------------------|
| version           | 否  | Integer | 只回傳符合指定版本的流程定義。                                                                            |
| name              | 否  | String  | 只回傳名稱等於指定值的流程定義。                                                                           |
| nameLike          | 否  | String  | 只回傳名稱符合 Like 條件的流程定義。                                                                      |
| key               | 否  | String  | 只回傳指定 key 的流程定義。                                                                           |
| keyLike           | 否  | String  | 只回傳 key 符合 Like 條件的流程定義。                                                                   |
| resourceName      | 否  | String  | 只回傳指定資源檔名的流程定義。                                                                            |
| resourceNameLike  | 否  | String  | 只回傳資源檔名符合 Like 條件的流程定義。                                                                    |
| category          | 否  | String  | 只回傳指定類別的流程定義。                                                                              |
| categoryLike      | 否  | String  | 只回傳類別符合 Like 條件的流程定義。                                                                      |
| categoryNotEquals | 否  | String  | 只回傳不屬於指定類別的流程定義。                                                                           |
| deploymentId      | 否  | String  | 只回傳屬於指定部署的流程定義。                                                                            |
| startableByUser   | 否  | String  | 只回傳可由指定使用者啟動的流程定義。                                                                         |
| latest            | 否  | Boolean | 僅回傳最新版本。僅能與 `key`、`keyLike`、`resourceName`、`resourceNameLike` 搭配，其餘參數若同時使用會回傳 400。         |
| suspended         | 否  | Boolean | `true` 回傳已暫停流程；`false` 回傳啟用中的流程。                                                           |
| sort              | 否  | String  | 排序欄位，可為 `'name'(預設)`、`'id'`、`'key'`、`'category'`、`'deploymentId'`、`'version'`，須搭配 `order`。 |

#### 回應碼

| 代碼  | 說明                                      |
|-----|-----------------------------------------|
| 200 | 請求成功並回傳流程定義清單。                          |
| 400 | 參數格式錯誤，或 `latest` 與不支援的參數同時使用。詳情請見狀態訊息。 |

#### 成功回應範例

```json
{
  "data": [
    {
      "id": "oneTaskProcess:1:4",
      "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4",
      "version": 1,
      "key": "oneTaskProcess",
      "category": "Examples",
      "suspended": false,
      "name": "The One Task Process",
      "description": "This is a process for testing purposes",
      "deploymentId": "2",
      "deploymentUrl": "http://localhost:8081/repository/deployments/2",
      "graphicalNotationDefined": true,
      "resource": "http://localhost:8182/repository/deployments/2/resources/testProcess.xml",
      "diagramResource": "http://localhost:8182/repository/deployments/2/resources/testProcess.png",
      "startFormDefined": false
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

- `graphicalNotationDefined`：是否包含 BPMN DI 圖形資訊。
- `resource`：實際部署的 BPMN 2.0 XML 檔案 URL。
- `diagramResource`：流程圖像資源 URL，若無圖像則為 `null`。

### 取得單一流程定義

`GET repository/process-definitions/{processDefinitionId}`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明           |
|---------------------|----|--------|--------------|
| processDefinitionId | 是  | String | 要取得的流程定義 ID。 |

#### 回應碼

| 代碼  | 說明         |
|-----|------------|
| 200 | 找到並回傳流程定義。 |
| 404 | 找不到指定流程定義。 |

#### 成功回應範例

與清單 API 範例相同，包含 `graphicalNotationDefined`、`resource`、`diagramResource` 等欄位。

### 更新流程定義類別

`PUT repository/process-definitions/{processDefinitionId}`

#### Request Body

```json
{
  "category": "updatedcategory"
}
```

#### 回應碼

| 代碼  | 說明                    |
|-----|-----------------------|
| 200 | 類別更新成功。               |
| 400 | 請求 body 未提供 category。 |
| 404 | 找不到指定流程定義。            |

> 成功回應內容同「取得單一流程定義」。

### 下載流程定義資源檔

`GET repository/process-definitions/{processDefinitionId}/resourcedata`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明               |
|---------------------|----|--------|------------------|
| processDefinitionId | 是  | String | 要下載資源內容的流程定義 ID。 |

回應與 `GET repository/deployments/{deploymentId}/resourcedata/{resourceId}` 完全相同，會直接回傳二進位內容。

### 取得流程定義的 BPMN Model

`GET repository/process-definitions/{processDefinitionId}/model`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明             |
|---------------------|----|--------|----------------|
| processDefinitionId | 是  | String | 要取得模型的流程定義 ID。 |

#### 回應碼

| 代碼  | 說明           |
|-----|--------------|
| 200 | 找到流程定義並回傳模型。 |
| 404 | 找不到指定流程定義。   |

#### 成功回應範例

回應為 `org.flowable.bpmn.model.BpmnModel` 的 JSON 內容，例如：

```json
{
  "processes": [
    {
      "id": "oneTaskProcess",
      "xmlRowNumber": 7,
      "xmlColumnNumber": 60,
      "extensionElements": {},
      "name": "The One Task Process",
      "executable": true,
      "documentation": "One task process description"
    }
  ]
}
```

### 暫停流程定義

`PUT repository/process-definitions/{processDefinitionId}`

#### Request Body

```json
{
  "action": "suspend",
  "includeProcessInstances": "false",
  "date": "2013-04-15T00:42:12Z"
}
```

#### JSON 參數說明

| 參數                      | 說明                               | 必填 |
|-------------------------|----------------------------------|----|
| action                  | 執行動作，必須為 `activate` 或 `suspend`。 | 是  |
| includeProcessInstances | 是否同步暫停/啟用現有流程實例；省略則不變更。          | 否  |
| date                    | 指定執行時間（ISO-8601），未提供則立即生效。       | 否  |

#### 回應碼

| 代碼  | 說明          |
|-----|-------------|
| 200 | 流程定義已暫停。    |
| 404 | 找不到指定流程定義。  |
| 409 | 流程定義已是暫停狀態。 |

> 成功回應內容同「取得單一流程定義」。

### 啟用流程定義

`PUT repository/process-definitions/{processDefinitionId}`

#### Request Body

```json
{
  "action": "activate",
  "includeProcessInstances": "true",
  "date": "2013-04-15T00:42:12Z"
}
```

JSON 參數同「暫停流程定義」。

#### 回應碼

| 代碼  | 說明          |
|-----|-------------|
| 200 | 流程定義已啟用。    |
| 404 | 找不到指定流程定義。  |
| 409 | 流程定義已是啟用狀態。 |

### 取得流程定義的候選啟動人/群組

`GET repository/process-definitions/{processDefinitionId}/identitylinks`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明           |
|---------------------|----|--------|--------------|
| processDefinitionId | 是  | String | 要查詢的流程定義 ID。 |

#### 回應碼

| 代碼  | 說明                        |
|-----|---------------------------|
| 200 | 找到流程定義並回傳 identity links。 |
| 404 | 找不到指定流程定義。                |

#### 成功回應範例

```json
[
  {
    "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4/identitylinks/groups/admin",
    "user": null,
    "group": "admin",
    "type": "candidate"
  },
  {
    "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4/identitylinks/users/kermit",
    "user": "kermit",
    "group": null,
    "type": "candidate"
  }
]
```

### 新增候選啟動條件

`POST repository/process-definitions/{processDefinitionId}/identitylinks`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明         |
|---------------------|----|--------|------------|
| processDefinitionId | 是  | String | 目標流程定義 ID。 |

#### Request Body 範例

- 指定使用者：
  ```json
  {
    "user" : "kermit"
  }
  ```
- 指定群組：
  ```json
  {
    "groupId" : "sales"
  }
  ```

#### 回應碼

| 代碼  | 說明         |
|-----|------------|
| 201 | 建立成功。      |
| 404 | 找不到指定流程定義。 |

#### 成功回應範例

```json
{
  "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4/identitylinks/users/kermit",
  "user": "kermit",
  "group": null,
  "type": "candidate"
}
```

### 移除候選啟動條件

`DELETE repository/process-definitions/{processDefinitionId}/identitylinks/{family}/{identityId}`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明                            |
|---------------------|----|--------|-------------------------------|
| processDefinitionId | 是  | String | 目標流程定義 ID。                    |
| family              | 是  | String | `users` 或 `groups`，決定從哪種類別移除。 |
| identityId          | 是  | String | 對應的 userId 或 groupId。         |

#### 回應碼

| 代碼  | 說明                             |
|-----|--------------------------------|
| 204 | 移除成功，無回應內容。                    |
| 404 | 找不到流程定義，或不存在對應的 identity link。 |

#### 成功回應範例

```json
{
  "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4/identitylinks/users/kermit",
  "user": "kermit",
  "group": null,
  "type": "candidate"
}
```

### 查詢特定候選啟動條件

`GET repository/process-definitions/{processDefinitionId}/identitylinks/{family}/{identityId}`

#### URL 參數

| 參數                  | 必填 | 類型     | 說明                     |
|---------------------|----|--------|------------------------|
| processDefinitionId | 是  | String | 目標流程定義 ID。             |
| family              | 是  | String | `users` 或 `groups`。    |
| identityId          | 是  | String | 要查詢的 userId 或 groupId。 |

#### 回應碼

| 代碼  | 說明                         |
|-----|----------------------------|
| 200 | 找到流程定義並回傳 identity link。   |
| 404 | 找不到流程定義或對應的 identity link。 |

#### 成功回應範例

```json
{
  "url": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4/identitylinks/users/kermit",
  "user": "kermit",
  "group": null,
  "type": "candidate"
}
```

<a id="process-instance-api"></a>
## 流程實例 API

### 取得流程實例

`GET runtime/process-instances/{processInstanceId}`

- URL 參數：`processInstanceId`（必填，String）
- 回應碼：200 表示找到並回傳；404 表示不存在。
- 範例回應：
  ```json
  {
    "id": "7",
    "url": "http://localhost:8182/runtime/process-instances/7",
    "businessKey": "myBusinessKey",
    "suspended": false,
    "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/processOne%3A1%3A4",
    "activityId": "processTask",
    "tenantId": null
  }
  ```

### 刪除流程實例

`DELETE runtime/process-instances/{processInstanceId}?deleteReason={deleteReason}`

| 參數                | 必填 | 類型     | 說明            |
|-------------------|----|--------|---------------|
| processInstanceId | 是  | String | 要刪除的流程實例 ID。  |
| deleteReason      | 否  | String | 刪除原因，若省略預設空值。 |

- 回應碼：204 成功刪除（無回應內容）、404 找不到實例。

### 啟用或暫停流程實例

`PUT runtime/process-instances/{processInstanceId}`

- URL 參數：`processInstanceId`（必填）。
- Request Body：
  ```json
  { "action": "suspend" }
  ```
  或
  ```json
  { "action": "activate" }
  ```
- 回應碼：200 執行成功；400 `action` 非法；404 找不到流程實例；409 目前狀態不允許（例如已暫停）。

### 啟動流程實例

`POST runtime/process-instances`

- Request Body 範例：
    - 依流程定義 ID：
      ```json
      {
        "processDefinitionId": "oneTaskProcess:1:158",
        "businessKey": "myBusinessKey",
        "returnVariables": true,
        "variables": [
          { "name": "myVar", "value": "This is a variable" }
        ]
      }
      ```
    - 依流程定義 key：
      ```json
      {
        "processDefinitionKey": "oneTaskProcess",
        "businessKey": "myBusinessKey",
        "returnVariables": false,
        "tenantId": "tenant1",
        "variables": [
          { "name": "myVar", "value": "This is a variable" }
        ]
      }
      ```
    - 依 message 啟動：
      ```json
      {
        "message": "newOrderMessage",
        "businessKey": "myBusinessKey",
        "tenantId": "tenant1",
        "variables": [
          { "name": "myVar", "value": "This is a variable" }
        ]
      }
      ```
- 也可傳入 `transientVariables`（結構同 variables），以及 `returnVariables` 控制回傳是否包含流程變數。
- `processDefinitionId`、`processDefinitionKey`、`message` 只能擇一；`businessKey`、`variables`、`tenantId` 選填。未提供
  tenantId 時使用預設租戶。流程變數一律是 local scope。
- 回應碼：201 建立成功；400 找不到定義或 message、或變數格式錯誤。
- 成功回應與「取得流程實例」格式相同。

### 查詢流程實例清單

`GET runtime/process-instances`

| 參數                      | 必填 | 類型      | 說明                                                                                    |
|-------------------------|----|---------|---------------------------------------------------------------------------------------|
| id                      | 否  | String  | 只回傳符合此 ID 的流程實例。                                                                      |
| processDefinitionKey    | 否  | String  | 限制為指定流程定義 key。                                                                        |
| processDefinitionId     | 否  | String  | 限制為指定流程定義 ID。                                                                         |
| businessKey             | 否  | String  | 只回傳指定 businessKey。                                                                    |
| involvedUser            | 否  | String  | 僅回傳指定使用者參與的流程。                                                                        |
| suspended               | 否  | Boolean | `true` 僅回傳暫停中的流程；`false` 僅回傳啟用中的流程。                                                   |
| superProcessInstanceId  | 否  | String  | 僅回傳擁有指定父流程 ID 的流程（Call Activity）。                                                     |
| subProcessInstanceId    | 否  | String  | 僅回傳作為子流程且 ID 相符者。                                                                     |
| excludeSubprocesses     | 否  | Boolean | `true` 只回傳非子流程。                                                                       |
| includeProcessVariables | 否  | Boolean | 是否在結果加入流程變數。                                                                          |
| tenantId                | 否  | String  | 只回傳指定租戶。                                                                              |
| tenantIdLike            | 否  | String  | 只回傳 tenantId 模糊比對。                                                                    |
| withoutTenantId         | 否  | Boolean | `true` 只回傳無 tenantId 設定的流程。                                                           |
| sort                    | 否  | String  | 排序欄位，可為 `id`(預設)、`processDefinitionId`、`tenantId`、`processDefinitionKey`，須搭配 `order`。 |

- 回應碼：200 成功；400 參數格式錯誤。
- 範例回應：
  ```json
  {
    "data": [
      {
        "id": "7",
        "url": "http://localhost:8182/runtime/process-instances/7",
        "businessKey": "myBusinessKey",
        "suspended": false,
        "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/processOne%3A1%3A4",
        "activityId": "processTask",
        "tenantId": null
      }
    ],
    "total": 2,
    "start": 0,
    "sort": "id",
    "order": "asc",
    "size": 2
  }
  ```

### 以 JSON 條件查詢流程實例

`POST query/process-instances`

```json
{
  "processDefinitionKey": "oneTaskProcess",
  "variables": [
    {
      "name": "myVariable",
      "value": 1234,
      "operation": "equals",
      "type": "long"
    }
  ]
}
```

- Request Body 可使用 GET 版本所有過濾條件，並可加入 `variables` 陣列（格式參考 REST 變數章節）。
- 支援一般分頁與排序參數。
- 回應碼：200 成功；400 參數格式錯誤。
- 成功回應與 GET 版本相同。

### 取得流程實例圖像

`GET runtime/process-instances/{processInstanceId}/diagram`

| 參數                | 必填 | 類型     | 說明               |
|-------------------|----|--------|------------------|
| processInstanceId | 是  | String | 要取得流程圖像的流程實例 ID。 |

- 回應碼：200 成功且回傳圖檔；400 找不到實例且流程定義沒有 BPMN DI；404 找不到實例。
- 成功回應：以 Binary Blob 形式回傳圖片，可能為 `null`。

### 管理流程實例參與者（Identity Links）

#### 查詢參與者

`GET runtime/process-instances/{processInstanceId}/identitylinks`

- 回應碼：200 成功回傳 JSON 陣列；404 找不到流程實例。
- 範例：
  ```json
  [
    {
      "url": "http://localhost:8182/runtime/process-instances/5/identitylinks/users/john/customType",
      "user": "john",
      "group": null,
      "type": "customType"
    },
    {
      "url": "http://localhost:8182/runtime/process-instances/5/identitylinks/users/paul/candidate",
      "user": "paul",
      "group": null,
      "type": "candidate"
    }
  ]
  ```
  > 只能指派使用者，`groupId` 永遠為 null。

#### 新增參與者

`POST runtime/process-instances/{processInstanceId}/identitylinks`

- Request Body：
  ```json
  {
    "userId": "kermit",
    "type": "participant"
  }
  ```
  `userId` 與 `type` 皆必填。
- 回應碼：201 建立成功；400 缺少欄位；404 找不到流程實例。

#### 移除參與者

`DELETE runtime/process-instances/{processInstanceId}/identitylinks/users/{userId}/{type}`

| 參數                | 必填 | 類型     | 說明                |
|-------------------|----|--------|-------------------|
| processInstanceId | 是  | String | 流程實例 ID。          |
| userId            | 是  | String | 要移除的使用者。          |
| type              | 是  | String | Identity link 類型。 |

- 回應碼：204 刪除成功；404 找不到流程實例或對應 link。

### 流程實例變數

#### 查詢全部變數

`GET runtime/process-instances/{processInstanceId}/variables`

- 回傳僅包含 local 變數。
- 回應碼：200 成功；404 找不到流程實例。
- 範例：
  ```json
  [
    { "name": "intProcVar", "type": "integer", "value": 123, "scope": "local" },
    {
      "name": "byteArrayProcVar",
      "type": "binary",
      "value": null,
      "valueUrl": "http://localhost:8182/runtime/process-instances/5/variables/byteArrayProcVar/data",
      "scope": "local"
    }
  ]
  ```
  > Binary/Serializable 變數以 `valueUrl` 方式提供內容。

#### 取得單一變數

`GET runtime/process-instances/{processInstanceId}/variables/{variableName}`

- 回應碼：200 成功；400 請求不完整；404 找不到流程實例或變數。

#### 建立（POST）或覆寫（PUT）多筆變數

`POST|PUT runtime/process-instances/{processInstanceId}/variables`

- POST：全部變數需不存在，否則 409；PUT：不存在就建立，存在即覆寫。
- Request Body：
  ```json
  [
    { "name": "intProcVar", "type": "integer", "value": 123 }
  ]
  ```
  > scope 會被忽略，流程實例僅支援 local 變數。
- 回應碼：201（POST 建立成功）、200（PUT 成功）；400 變數格式錯誤；404 找不到流程實例；409 POST 時變數已存在。

#### 更新單一變數

`PUT runtime/process-instances/{processInstanceId}/variables/{variableName}`

- Request Body：
  ```json
  {
    "name": "intProcVar",
    "type": "integer",
    "value": 123
  }
  ```
- 回應碼：200 成功；404 找不到流程實例或變數。

#### 新增二進位變數

`POST runtime/process-instances/{processInstanceId}/variables`（multipart/form-data）

- 表單欄位：
    - `file`：唯一檔案欄位，為變數值。
    - `name`（必填）：變數名稱。
    - `type`（選填）：省略則視為 `binary`。
- 回應碼：201 建立成功；400 缺少名稱；404 找不到流程實例；409 變數已存在；415 序列化資料無法還原。

#### 更新二進位變數

`PUT runtime/process-instances/{processInstanceId}/variables` 或 `PUT .../{variableName}`（multipart/form-data）

- 表單欄位同上。
- 回應碼：200 更新成功；400 缺少名稱；404 找不到流程實例或變數；415 無法反序列化。

<a id="execution-api"></a>
## 執行（Execution）API

### 取得單一執行緒

`GET runtime/executions/{executionId}`

- URL 參數：`executionId`（必填）。
- 回應碼：200 成功；404 找不到執行緒。
- 範例：
  ```json
  {
    "id": "5",
    "url": "http://localhost:8182/runtime/executions/5",
    "parentId": null,
    "parentUrl": null,
    "processInstanceId": "5",
    "processInstanceUrl": "http://localhost:8182/runtime/process-instances/5",
    "suspended": false,
    "activityId": null,
    "tenantId": null
  }
  ```

### 對執行緒進行動作

`PUT runtime/executions/{executionId}`

常見 Request Body：

```json
{
  "action": "signal"
}
```

```json
{
  "action": "signal",
  "variables": [
    {
      "name": "myVar",
      "value": "someValue"
    }
  ]
}
```

```json
{
  "action": "signalEventReceived",
  "signalName": "mySignal",
  "variables": []
}
```

```json
{
  "action": "messageEventReceived",
  "messageName": "myMessage",
  "variables": []
}
```

- `signalEventReceived` 需提供 `signalName`；`messageEventReceived` 需 `messageName`。均可附帶 `variables` 或
  `transientVariables`。
- 回應碼：200 動作已執行；204 動作執行後執行緒已結束；400 動作非法或缺少參數；404 找不到執行緒。

### 查詢執行緒活躍活動

`GET runtime/executions/{executionId}/activities`

- 回應碼：200 成功；404 找不到執行緒。
- 範例：`[ "userTaskForManager", "receiveTask" ]`

### 查詢執行緒清單

`GET runtime/executions`

| 參數                           | 必填 | 類型      | 說明                                                                                    |
|------------------------------|----|---------|---------------------------------------------------------------------------------------|
| id                           | 否  | String  | 僅回傳指定執行緒。                                                                             |
| activityId                   | 否  | String  | 依活動 ID 過濾。                                                                            |
| processDefinitionKey         | 否  | String  | 依流程定義 key 過濾。                                                                         |
| processDefinitionId          | 否  | String  | 依流程定義 ID 過濾。                                                                          |
| processInstanceId            | 否  | String  | 限制在指定流程實例內。                                                                           |
| messageEventSubscriptionName | 否  | String  | 僅回傳訂閱指定 Message 的執行緒。                                                                 |
| signalEventSubscriptionName  | 否  | String  | 僅回傳訂閱指定 Signal 的執行緒。                                                                  |
| parentId                     | 否  | String  | 僅回傳指定父執行緒的直接子執行。                                                                      |
| tenantId                     | 否  | String  | 限制租戶。                                                                                 |
| tenantIdLike                 | 否  | String  | 租戶模糊查詢。                                                                               |
| withoutTenantId              | 否  | Boolean | `true` 僅回傳無租戶執行。                                                                      |
| sort                         | 否  | String  | 排序欄位：`processInstanceId`(預設)、`processDefinitionId`、`processDefinitionKey`、`tenantId`。 |

- 回應碼：200 成功；400 參數格式錯誤。

### 以 JSON 條件查詢執行緒

`POST query/executions`

```json
{
  "processDefinitionKey": "oneTaskProcess",
  "variables": [
    {
      "name": "myVariable",
      "value": 1234,
      "operation": "equals",
      "type": "long"
    }
  ],
  "processInstanceVariables": [
    {
      "name": "processVariable",
      "value": "some string",
      "operation": "equals",
      "type": "string"
    }
  ]
}
```

- 支援 GET 版本的全部過濾條件，另可加入 `variables` 與 `processInstanceVariables` 陣列。
- 回應碼：200 成功；400 參數錯誤。

### 執行緒變數

#### 查詢所有變數

`GET runtime/executions/{executionId}/variables?scope={scope}`

| 參數          | 必填 | 說明                            |
|-------------|----|-------------------------------|
| executionId | 是  | 執行緒 ID。                       |
| scope       | 否  | `local` 或 `global`；省略時同時回傳兩種。 |

- 回應碼：200 成功；404 找不到執行緒。

#### 取得單一變數

`GET runtime/executions/{executionId}/variables/{variableName}?scope={scope}`

- 若未指定 scope，優先回傳 local，否則取 global。
- 回應碼：200 成功；400 參數錯誤；404 找不到執行或該範圍內無變數。

#### 建立（POST）或覆寫（PUT）多筆變數

`POST|PUT runtime/executions/{executionId}/variables`

- POST 僅能建立 scope 相同的新變數，若任一變數已存在則回傳 409。
- Request Body：
  ```json
  [
    { "name": "intProcVar", "type": "integer", "value": 123, "scope": "local" }
  ]
  ```
- 回應碼：201 建立成功；400 變數格式錯誤或 scope 混用；404 找不到執行緒；409 POST 時變數已存在。

#### 更新單一變數

`PUT runtime/executions/{executionId}/variables/{variableName}`

- Request Body：
  ```json
  {
    "name": "intProcVar",
    "type": "integer",
    "value": 123,
    "scope": "global"
  }
  ```
- 回應碼：200 成功；404 找不到執行或變數。

#### 新增二進位變數

`POST runtime/executions/{executionId}/variables`（multipart/form-data）

- 表單欄位：`file`、`name`(必填)、`type`(選填，預設 binary)、`scope`(選填，預設 local)。
- 回應碼：201 成功；400 缺欄位；404 找不到執行；409 變數已存在；415 無法反序列化。

#### 更新二進位變數

`PUT runtime/executions/{executionId}/variables/{variableName}`（multipart/form-data）

- 表單欄位同上。
- 回應碼：200 成功；400 缺欄位；404 找不到執行或變數；415 無法反序列化。

<a id="task-api"></a>
## 任務 API

### 取得指定任務

`GET runtime/tasks/{taskId}`

- URL 參數：`taskId`（必填）。
- 回應碼：200 成功；404 找不到任務。
- 範例：
  ```json
  {
    "assignee": "kermit",
    "createTime": "2013-04-17T10:17:43.902+0000",
    "delegationState": "pending",
    "description": "Task description",
    "dueDate": "2013-04-17T10:17:43.902+0000",
    "execution": "http://localhost:8182/runtime/executions/5",
    "id": "8",
    "name": "My task",
    "owner": "owner",
    "parentTask": "http://localhost:8182/runtime/tasks/9",
    "priority": 50,
    "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4",
    "processInstanceUrl": "http://localhost:8182/runtime/process-instances/5",
    "suspended": false,
    "taskDefinitionKey": "theTask",
    "url": "http://localhost:8182/runtime/tasks/8",
    "tenantId": null
  }
  ```
  > `delegationState` 可能為 `null`、`pending`、`resolved`。

### 查詢任務清單

`GET runtime/tasks`

| 參數                             | 必填 | 類型       | 說明                                  |
|--------------------------------|----|----------|-------------------------------------|
| name                           | 否  | String   | 只回傳名稱等於指定值的任務。                      |
| nameLike                       | 否  | String   | 名稱模糊查詢。                             |
| description                    | 否  | String   | 指定描述。                               |
| priority                       | 否  | Integer  | 指定優先權。                              |
| minimumPriority                | 否  | Integer  | 優先權 >= 指定值。                         |
| maximumPriority                | 否  | Integer  | 優先權 <= 指定值。                         |
| assignee                       | 否  | String   | 指定負責人。                              |
| assigneeLike                   | 否  | String   | 負責人模糊查詢。                            |
| owner                          | 否  | String   | 指定擁有者。                              |
| ownerLike                      | 否  | String   | 擁有者模糊查詢。                            |
| unassigned                     | 否  | Boolean  | `true` 只回傳尚未指派的任務。                  |
| delegationState                | 否  | String   | 僅 `pending` 或 `resolved`。           |
| candidateUser                  | 否  | String   | 只回傳該使用者可領取的任務（含群組）。                 |
| candidateGroup                 | 否  | String   | 只回傳該群組成員可領取的任務。                     |
| candidateGroups                | 否  | String   | 多群組（以逗號分隔）。                         |
| involvedUser                   | 否  | String   | 指定使用者參與的任務。                         |
| taskDefinitionKey              | 否  | String   | 指定任務定義 ID。                          |
| taskDefinitionKeyLike          | 否  | String   | 任務定義 ID 模糊查詢。                       |
| processInstanceId              | 否  | String   | 指定流程實例。                             |
| processInstanceBusinessKey     | 否  | String   | 指定流程 businessKey。                   |
| processInstanceBusinessKeyLike | 否  | String   | 流程 businessKey 模糊查詢。                |
| processDefinitionId            | 否  | String   | 指定流程定義 ID。                          |
| processDefinitionKey           | 否  | String   | 指定流程定義 key。                         |
| processDefinitionKeyLike       | 否  | String   | 流程定義 key 模糊查詢。                      |
| processDefinitionName          | 否  | String   | 指定流程定義名稱。                           |
| processDefinitionNameLike      | 否  | String   | 流程定義名稱模糊查詢。                         |
| executionId                    | 否  | String   | 指定執行 ID。                            |
| createdOn                      | 否  | ISO Date | 指定建立日期。                             |
| createdBefore                  | 否  | ISO Date | 建立日期早於。                             |
| createdAfter                   | 否  | ISO Date | 建立日期晚於。                             |
| dueOn                          | 否  | ISO Date | 到期日等於。                              |
| dueBefore                      | 否  | ISO Date | 到期日前。                               |
| dueAfter                       | 否  | ISO Date | 到期日後。                               |
| withoutDueDate                 | 否  | Boolean  | `true` 只回傳無到期日。                     |
| excludeSubTasks                | 否  | Boolean  | `true` 排除子任務。                       |
| active                         | 否  | Boolean  | `true` 只回傳未暫停；`false` 僅回傳隸屬暫停流程的任務。 |
| includeTaskLocalVariables      | 否  | Boolean  | 是否回傳任務層級變數。                         |
| includeProcessVariables        | 否  | Boolean  | 是否回傳流程變數。                           |
| tenantId                       | 否  | String   | 指定租戶。                               |
| tenantIdLike                   | 否  | String   | 租戶模糊查詢。                             |
| withoutTenantId                | 否  | Boolean  | `true` 只回傳無租戶。                      |
| candidateOrAssigned            | 否  | String   | 回傳已指派給使用者或使用者可領取的任務。                |
| category                       | 否  | String   | 任務分類（非流程定義分類）。                      |

- 回應碼：200 成功；400 參數錯誤或 `delegationState` 無效。
- 回應格式同「取得任務」。

### JSON 查詢任務

`POST query/tasks`

- 支援 GET 所有條件，並可使用 `candidateGroupIn`（僅 POST 版有）。
- 可透過 `taskVariables`、`processInstanceVariables` 依變數過濾。
- Request Body 範例：
  ```json
  {
    "name": "My task",
    "description": "The task description",
    "taskVariables": [
      { "name": "myVariable", "value": 1234, "operation": "equals", "type": "long" }
    ]
  }
  ```
- 回應碼：200 成功；400 參數錯誤。

### 更新任務

`PUT runtime/tasks/{taskId}`

```json
{
  "assignee": "assignee",
  "delegationState": "resolved",
  "description": "New task description",
  "dueDate": "2013-04-17T13:06:02.438+02:00",
  "name": "New task name",
  "owner": "owner",
  "parentTaskId": "3",
  "priority": 20
}
```

- 所有欄位皆選填，顯式設定為 `null` 會清空欄位。
- 回應碼：200 成功；404 找不到任務；409 代表同時被其他人更新。

### 任務動作

`POST runtime/tasks/{taskId}`

- 完成任務：
  ```json
  { "action": "complete", "variables": [] }
  ```
  > `variables` 與 `transientVariables` 結構同 REST 變數，scope 會被忽略。
- Claim 任務：
  ```json
  { "action": "claim", "assignee": "userWhoClaims" }
  ```
- Delegate 任務：
  ```json
  { "action": "delegate", "assignee": "userToDelegateTo" }
  ```
- Resolve 任務：
  ```json
  { "action": "resolve" }
  ```

回應碼：200 成功；400 內容非法或缺少 assignee；404 找不到任務；409 因衝突無法執行（例如已被他人 claim）。

### 刪除任務

`DELETE runtime/tasks/{taskId}?cascadeHistory={cascadeHistory}&deleteReason={deleteReason}`

| 參數             | 必填 | 類型      | 說明                               |
|----------------|----|---------|----------------------------------|
| taskId         | 是  | String  | 任務 ID。                           |
| cascadeHistory | 否  | Boolean | 是否連帶刪除 HistoricTask，預設 false。    |
| deleteReason   | 否  | String  | 刪除原因（`cascadeHistory=true` 時忽略）。 |

- 回應碼：204 成功刪除；403 任務屬於流程不允許刪除；404 找不到任務。

### 任務變數

#### 取得全部變數

`GET runtime/tasks/{taskId}/variables?scope={scope}`

- `scope` 可為 `local`、`global` 或省略（回傳兩者）。
- 回應碼：200 成功；404 找不到任務。

#### 取得單一變數

`GET runtime/tasks/{taskId}/variables/{variableName}?scope={scope}`

- 回應碼：200 成功；404 找不到任務或變數。

#### 取得變數二進位內容

`GET runtime/tasks/{taskId}/variables/{variableName}/data?scope={scope}`

- 僅支援 binary/serializable 變數。
- 回應碼：200 成功；404 任務或變數不存在，或不是 binary。成功時直接回傳 binary 串流，Content-Type 視資料型態而定。

#### 建立多個（非 binary）變數

`POST runtime/tasks/{taskId}/variables`

- Request Body 為陣列，例如：
  ```json
  [
    { "name": "myTaskVariable", "scope": "local", "type": "string", "value": "Hello my friend" }
  ]
  ```
- 注意：
    - `name` 必填。
    - scope 省略視為 local；若為獨立任務（無流程）則不可設定 global。
    - 陣列為空或缺少陣列結構皆會 400。
- 回應碼：201 建立成功；400 參數錯誤；404 找不到任務；409 變數已存在。

#### 建立二進位變數

`POST runtime/tasks/{taskId}/variables`（multipart/form-data）

- 表單欄位：`file`、`name`(必填)、`scope`(預設 local)、`type`(未填則 binary)。
- 回應碼：201 成功；400 缺欄位或在獨立任務上建立 global；404 找不到任務；409 已存在；415 無法反序列化。

#### 更新簡單變數

`PUT runtime/tasks/{taskId}/variables/{variableName}`

- Request Body：
  ```json
  { "name": "myTaskVariable", "scope": "local", "type": "string", "value": "Hello my friend" }
  ```
- 回應碼：200 成功；400 缺欄位或獨立任務使用 global；404 任務或變數不存在。

#### 更新二進位變數

`PUT runtime/tasks/{taskId}/variables/{variableName}`（multipart/form-data）

- 表單欄位同建立二進位變數。
- 回應碼：200 成功；400 缺欄位或獨立任務使用 global；404 任務或變數不存在；415 無法反序列化。

#### 刪除單一變數

`DELETE runtime/tasks/{taskId}/variables/{variableName}?scope={scope}`

- `scope` 省略時視為 local。
- 回應碼：204 成功；404 任務或變數不存在。

#### 刪除全部任務在地變數

`DELETE runtime/tasks/{taskId}/variables`

- 回應碼：204 成功刪除所有 local 變數；404 任務不存在。

### 任務 Identity Links

#### 查詢全部身分連結

`GET runtime/tasks/{taskId}/identitylinks`

- 回應碼：200 成功；404 任務不存在。
- 範例：
  ```json
  [
    {
      "userId": "kermit",
      "groupId": null,
      "type": "candidate",
      "url": "http://localhost:8081/flowable-rest/service/runtime/tasks/100/identitylinks/users/kermit/candidate"
    },
    {
      "userId": null,
      "groupId": "sales",
      "type": "candidate",
      "url": "http://localhost:8081/flowable-rest/service/runtime/tasks/100/identitylinks/groups/sales/candidate"
    }
  ]
  ```

#### 僅查詢使用者或群組連結

`GET runtime/tasks/{taskId}/identitylinks/users`
`GET runtime/tasks/{taskId}/identitylinks/groups`

- 回應格式同完整清單。

#### 取得特定連結

`GET runtime/tasks/{taskId}/identitylinks/{family}/{identityId}/{type}`

| 參數         | 說明                  |
|------------|---------------------|
| taskId     | 任務 ID。              |
| family     | `users` 或 `groups`。 |
| identityId | 使用者或群組 ID。          |
| type       | identity link 類型。   |

- 回應碼：200 成功；404 任務未找到或無此連結。

#### 新增連結

`POST runtime/tasks/{taskId}/identitylinks`

- Request Body（使用者）：
  ```json
  { "userId": "kermit", "type": "candidate" }
  ```
- Request Body（群組）：
  ```json
  { "groupId": "sales", "type": "candidate" }
  ```
- 回應碼：201 成功；404 任務或參數錯誤。

#### 刪除連結

`DELETE runtime/tasks/{taskId}/identitylinks/{family}/{identityId}/{type}`

- 回應碼：204 成功；404 任務或連結不存在。

### 任務評論

#### 建立評論

`POST runtime/tasks/{taskId}/comments`

```json
{
  "message": "This is a comment on the task.",
  "saveProcessInstanceId": true
}
```

- `message` 必填；`saveProcessInstanceId` 控制是否儲存流程實例 ID。
- 回應碼：201 成功；400 缺少留言；404 任務不存在。

#### 查詢全部評論

`GET runtime/tasks/{taskId}/comments`

- 回應碼：200 成功；404 任務不存在。

#### 取得單一評論

`GET runtime/tasks/{taskId}/comments/{commentId}`

- 回應碼：200 成功；404 任務不存在或無該評論。

#### 刪除評論

`DELETE runtime/tasks/{taskId}/comments/{commentId}`

- 回應碼：204 成功；404 任務或評論不存在。

### 任務事件

#### 查詢全部事件

`GET runtime/tasks/{taskId}/events`

- 回應碼：200 成功；404 任務不存在。
- 範例：
  ```json
  [
    {
      "action": "AddUserLink",
      "id": "4",
      "message": ["gonzo", "contributor"],
      "taskUrl": "http://localhost:8182/runtime/tasks/2",
      "time": "2013-05-17T11:50:50.000+0000",
      "url": "http://localhost:8182/runtime/tasks/2/events/4",
      "userId": null
    }
  ]
  ```

#### 取得單一事件

`GET runtime/tasks/{taskId}/events/{eventId}`

- 回應碼：200 成功；404 任務或事件不存在。

### 任務附件

#### 建立連結型附件

`POST runtime/tasks/{taskId}/attachments`

```json
{
  "name": "Simple attachment",
  "description": "Simple attachment description",
  "type": "simpleType",
  "externalUrl": "https://flowable.org"
}
```

- `name` 必填。
- 回應碼：201 成功；400 缺少名稱；404 任務不存在。

#### 建立上傳檔案附件

`POST runtime/tasks/{taskId}/attachments`（multipart/form-data）

- 表單欄位：`file` 必填；`name` 必填；`description`、`type` 選填。
- 回應碼：201 成功；400 缺欄位或未附檔；404 任務不存在。

#### 查詢全部附件

`GET runtime/tasks/{taskId}/attachments`

- 回應碼：200 成功；404 任務不存在。

#### 取得單一附件

`GET runtime/tasks/{taskId}/attachments/{attachmentId}`

- 成功回應含 `externalUrl` 或 `contentUrl`。若 `type` 為合法 MIME 類型，下載內容將套用同樣的 Content-Type。
- 回應碼：200 成功；404 任務或附件不存在。

#### 下載附件內容

`GET runtime/tasks/{taskId}/attachment/{attachmentId}/content`

- 若附件僅為外部 URL，會回傳 404。
- 回應碼：200 成功（串流 binary）；404 任務或附件不存在或無內容。

#### 刪除附件

`DELETE runtime/tasks/{taskId}/attachments/{attachmentId}`

- 回應碼：204 成功；404 任務或附件不存在。

<a id="history-api"></a>
## 歷史資料（History）

### 歷史流程實例

#### 取得單一歷史流程實例

`GET history/historic-process-instances/{processInstanceId}`

| 回應碼 | 說明               |
|-----|------------------|
| 200 | 找到對應歷史流程實例並回傳內容。 |
| 404 | 查無此歷史流程實例。       |

```json
{
  "data": [
    {
      "id": "5",
      "businessKey": "myKey",
      "processDefinitionId": "oneTaskProcess%3A1%3A4",
      "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4",
      "startTime": "2013-04-17T10:17:43.902+0000",
      "endTime": "2013-04-18T14:06:32.715+0000",
      "durationInMillis": 86400056,
      "startUserId": "kermit",
      "startActivityId": "startEvent",
      "endActivityId": "endEvent",
      "deleteReason": null,
      "superProcessInstanceId": "3",
      "url": "http://localhost:8182/history/historic-process-instances/5",
      "variables": null,
      "tenantId": null
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

#### 查詢歷史流程實例清單

`GET history/historic-process-instances`

| 參數                      | 型別      | 必填 | 說明                        |
|-------------------------|---------|----|---------------------------|
| processInstanceId       | String  | 否  | 指定歷史流程實例 ID。              |
| processDefinitionKey    | String  | 否  | 指定流程定義 key。               |
| processDefinitionId     | String  | 否  | 指定流程定義 ID。                |
| businessKey             | String  | 否  | 指定流程 businessKey。         |
| involvedUser            | String  | 否  | 僅回傳曾參與該流程的使用者。            |
| finished                | Boolean | 否  | 僅回傳已完成的實例。                |
| superProcessInstanceId  | String  | 否  | 依父流程 ID 篩選。               |
| excludeSubprocesses     | Boolean | 否  | true 時僅回傳非子流程。            |
| finishedAfter           | Date    | 否  | 僅回傳在此日期後結束。               |
| finishedBefore          | Date    | 否  | 僅回傳在此日期前結束。               |
| startedAfter            | Date    | 否  | 僅回傳在此日期後啟動。               |
| startedBefore           | Date    | 否  | 僅回傳在此日期前啟動。               |
| startedBy               | String  | 否  | 僅回傳由指定使用者啟動。              |
| includeProcessVariables | Boolean | 否  | true 時回傳歷史流程變數。           |
| tenantId                | String  | 否  | 僅回傳符合 tenantId。           |
| tenantIdLike            | String  | 否  | tenantId 模糊查詢。            |
| withoutTenantId         | Boolean | 否  | true 時僅回傳 tenantId 為空的紀錄。 |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 400 | 參數格式錯誤。 |

#### 進階查詢歷史流程實例

`POST query/historic-process-instances`

Request Body 範例：

```json
{
  "processDefinitionId": "oneTaskProcess%3A1%3A4",
  "variables": [
    {
      "name": "myVariable",
      "value": 1234,
      "operation": "equals",
      "type": "long"
    }
  ]
}
```

- JSON 欄位與 `GET history/historic-process-instances` 相同，改以 body 傳遞可避免 URL 過長並支援多條件。
- `variables` 為陣列，可指定變數名稱、型別與比較運算（equals/ notEquals/ greaterThan...）。

| 回應碼 | 說明           |
|-----|--------------|
| 200 | 查詢成功並回傳實例資料。 |
| 400 | 參數格式錯誤。      |

#### 刪除歷史流程實例

`DELETE history/historic-process-instances/{processInstanceId}`

| 回應碼 | 說明         |
|-----|------------|
| 200 | 歷史流程實例已刪除。 |
| 404 | 找不到指定流程實例。 |

#### 歷史流程實例 Identity Links

`GET history/historic-process-instance/{processInstanceId}/identitylinks`

| 回應碼 | 說明                      |
|-----|-------------------------|
| 200 | 查詢成功並回傳 identity links。 |
| 404 | 找不到對應流程實例。              |

```json
[
  {
    "type": "participant",
    "userId": "kermit",
    "groupId": null,
    "taskId": null,
    "taskUrl": null,
    "processInstanceId": "5",
    "processInstanceUrl": "http://localhost:8182/history/historic-process-instances/5"
  }
]
```

#### 取得歷史流程實例的二進位變數

`GET history/historic-process-instances/{processInstanceId}/variables/{variableName}/data`

| 回應碼 | 說明                    |
|-----|-----------------------|
| 200 | 找到流程實例並回傳指定變數的二進位內容。  |
| 404 | 查無實例、變數不存在或變數沒有二進位內容。 |

> 回應內容為原始 binary；若 type=binary 則 Content-Type 為 `application/octet-stream`，序列化物件則為
`application/x-java-serialized-object`。

### 歷史流程實例評論

#### 建立評論

`POST history/historic-process-instances/{processInstanceId}/comments`

Request Body：

```json
{
  "message": "This is a comment.",
  "saveProcessInstanceId": true
}
```

- `saveProcessInstanceId` 選填，true 時會一併儲存流程實例 ID。

| 回應碼 | 說明           |
|-----|--------------|
| 201 | 建立成功並回傳評論內容。 |
| 400 | 缺少 message。  |
| 404 | 找不到歷史流程實例。   |

```json
{
  "id": "123",
  "taskUrl": "http://localhost:8081/flowable-rest/service/runtime/tasks/101/comments/123",
  "processInstanceUrl": "http://localhost:8081/flowable-rest/service/history/historic-process-instances/100/comments/123",
  "message": "This is a comment on the task.",
  "author": "kermit",
  "time": "2014-07-13T13:13:52.232+08:00",
  "taskId": "101",
  "processInstanceId": "100"
}
```

#### 查詢全部評論

`GET history/historic-process-instances/{processInstanceId}/comments`

| 回應碼 | 說明       |
|-----|----------|
| 200 | 查詢成功。    |
| 404 | 找不到流程實例。 |

```json
[
  {
    "id": "123",
    "processInstanceUrl": "http://localhost:8081/flowable-rest/service/history/historic-process-instances/100/comments/123",
    "message": "This is a comment on the task.",
    "author": "kermit",
    "time": "2014-07-13T13:13:52.232+08:00",
    "processInstanceId": "100"
  },
  {
    "id": "456",
    "processInstanceUrl": "http://localhost:8081/flowable-rest/service/history/historic-process-instances/100/comments/456",
    "message": "This is another comment.",
    "author": "gonzo",
    "time": "2014-07-14T15:16:52.232+08:00",
    "processInstanceId": "100"
  }
]
```

#### 取得單一評論

`GET history/historic-process-instances/{processInstanceId}/comments/{commentId}`

| 回應碼 | 說明              |
|-----|-----------------|
| 200 | 找到流程實例與評論並回傳。   |
| 404 | 找不到流程實例或該評論不存在。 |

#### 刪除評論

`DELETE history/historic-process-instances/{processInstanceId}/comments/{commentId}`

| 回應碼 | 說明            |
|-----|---------------|
| 204 | 刪除成功。         |
| 404 | 找不到流程實例或無該評論。 |

### 歷史任務實例

#### 取得單一歷史任務

`GET history/historic-task-instances/{taskId}`

| 回應碼 | 說明           |
|-----|--------------|
| 200 | 找到歷史任務並回傳內容。 |
| 404 | 查無此任務。       |

```json
{
  "id": "5",
  "processDefinitionId": "oneTaskProcess%3A1%3A4",
  "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4",
  "processInstanceId": "3",
  "processInstanceUrl": "http://localhost:8182/history/historic-process-instances/3",
  "executionId": "4",
  "name": "My task name",
  "description": "My task description",
  "deleteReason": null,
  "owner": "kermit",
  "assignee": "fozzie",
  "startTime": "2013-04-17T10:17:43.902+0000",
  "endTime": "2013-04-18T14:06:32.715+0000",
  "durationInMillis": 86400056,
  "workTimeInMillis": 234890,
  "claimTime": "2013-04-18T11:01:54.715+0000",
  "taskDefinitionKey": "taskKey",
  "formKey": null,
  "priority": 50,
  "dueDate": "2013-04-20T12:11:13.134+0000",
  "parentTaskId": null,
  "url": "http://localhost:8182/history/historic-task-instances/5",
  "variables": null,
  "tenantId": null
}
```

#### 查詢歷史任務清單

`GET history/historic-task-instances`

| 參數                                                         | 型別      | 說明                     |
|------------------------------------------------------------|---------|------------------------|
| taskId                                                     | String  | 指定歷史任務 ID。             |
| processInstanceId                                          | String  | 依流程實例 ID 篩選。           |
| processDefinitionKey / processDefinitionKeyLike            | String  | 指定流程定義 key 或模糊匹配。      |
| processDefinitionId                                        | String  | 指定流程定義 ID。             |
| processDefinitionName / processDefinitionNameLike          | String  | 指定流程定義名稱或模糊匹配。         |
| processBusinessKey / processBusinessKeyLike                | String  | 依流程 businessKey（支援模糊）。 |
| executionId                                                | String  | 指定執行 ID。               |
| taskDefinitionKey                                          | String  | 任務在流程定義中的 key。         |
| taskName / taskNameLike                                    | String  | 任務名稱（支援模糊）。            |
| taskDescription / taskDescriptionLike                      | String  | 任務描述（支援模糊）。            |
| taskCategory                                               | String  | 任務分類（非流程分類）。           |
| taskDeleteReason / taskDeleteReasonLike                    | String  | 任務刪除原因（支援模糊）。          |
| taskAssignee / taskAssigneeLike                            | String  | 被指派者（支援模糊）。            |
| taskOwner / taskOwnerLike                                  | String  | 任務擁有者（支援模糊）。           |
| taskInvolvedUser                                           | String  | 指定參與者。                 |
| taskPriority                                               | Integer | 優先權。                   |
| finished                                                   | Boolean | 僅回傳已完成任務。              |
| processFinished                                            | Boolean | 依流程是否完成。               |
| parentTaskId                                               | String  | 指定父任務。                 |
| dueDate / dueDateAfter / dueDateBefore                     | Date    | 依到期日等條件篩選。             |
| withoutDueDate                                             | Boolean | true 時僅含無到期日任務。        |
| taskCompletedOn / taskCompletedAfter / taskCompletedBefore | Date    | 依完成時間篩選。               |
| taskCreatedOn / taskCreatedBefore / taskCreatedAfter       | Date    | 依建立時間篩選。               |
| includeTaskLocalVariables                                  | Boolean | true 時回傳任務 local 變數。   |
| includeProcessVariables                                    | Boolean | true 時回傳流程變數。          |
| tenantId / tenantIdLike                                    | String  | 指定（模糊）租戶。              |
| withoutTenantId                                            | Boolean | true 時僅回傳無租戶資料。        |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 400 | 參數格式錯誤。 |

#### 進階查詢歷史任務

`POST query/historic-task-instances`

Request Body 與 `GET` 參數相同，並可加入 `taskVariables`、`processVariables` 陣列以變數值過濾：

```json
{
  "processDefinitionId": "oneTaskProcess%3A1%3A4",
  "variables": [
    {
      "name": "myVariable",
      "value": 1234,
      "operation": "equals",
      "type": "long"
    }
  ]
}
```

| 回應碼 | 說明           |
|-----|--------------|
| 200 | 查詢成功並回傳任務資料。 |
| 400 | 參數格式錯誤。      |

#### 刪除歷史任務

`DELETE history/historic-task-instances/{taskId}`

| 回應碼 | 說明       |
|-----|----------|
| 200 | 歷史任務已刪除。 |
| 404 | 找不到指定任務。 |

#### 歷史任務 Identity Links

`GET history/historic-task-instance/{taskId}/identitylinks`

| 回應碼 | 說明                      |
|-----|-------------------------|
| 200 | 查詢成功並回傳 identity links。 |
| 404 | 找不到歷史任務。                |

```json
[
  {
    "type": "assignee",
    "userId": "kermit",
    "groupId": null,
    "taskId": "6",
    "taskUrl": "http://localhost:8182/history/historic-task-instances/5",
    "processInstanceId": null,
    "processInstanceUrl": null
  }
]
```

#### 取得歷史任務變數的二進位內容

`GET history/historic-task-instances/{taskId}/variables/{variableName}/data`

| 回應碼 | 說明                         |
|-----|----------------------------|
| 200 | 已找到任務並回傳變數的 binary stream。 |
| 404 | 任務不存在、變數不存在或該變數沒有二進位內容。    |

### 歷史活動實例

#### 查詢歷史活動清單

`GET history/historic-activity-instances`

| 參數                      | 型別      | 說明                    |
|-------------------------|---------|-----------------------|
| activityId              | String  | 指定活動 ID。              |
| activityInstanceId      | String  | 指定歷史活動實例 ID。          |
| activityName            | String  | 活動名稱。                 |
| activityType            | String  | BPMN 元素型別，例 userTask。 |
| executionId             | String  | 執行 ID。                |
| finished                | Boolean | 僅回傳已完成活動。             |
| taskAssignee            | String  | 依任務負責人。               |
| processInstanceId       | String  | 指定流程實例。               |
| processDefinitionId     | String  | 指定流程定義。               |
| tenantId / tenantIdLike | String  | 指定（模糊）租戶。             |
| withoutTenantId         | Boolean | 僅回傳無租戶資料。             |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 400 | 參數格式錯誤。 |

```json
{
  "data": [
    {
      "id": "5",
      "activityId": "4",
      "activityName": "My user task",
      "activityType": "userTask",
      "processDefinitionId": "oneTaskProcess%3A1%3A4",
      "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/oneTaskProcess%3A1%3A4",
      "processInstanceId": "3",
      "processInstanceUrl": "http://localhost:8182/history/historic-process-instances/3",
      "executionId": "4",
      "taskId": "4",
      "calledProcessInstanceId": null,
      "assignee": "fozzie",
      "startTime": "2013-04-17T10:17:43.902+0000",
      "endTime": "2013-04-18T14:06:32.715+0000",
      "durationInMillis": 86400056,
      "tenantId": null
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

#### 進階查詢歷史活動

`POST query/historic-activity-instances`

- Body 欄位與 `GET` 相同，採 JSON 傳遞以支援複雜條件。
- 回應碼：200 成功；400 參數不合法。

### 歷史變數實例

#### 取得歷史變數清單

`GET history/historic-variable-instances`

| 參數                   | 型別      | 說明            |
|----------------------|---------|---------------|
| processInstanceId    | String  | 指定流程實例。       |
| taskId               | String  | 指定任務 ID。      |
| excludeTaskVariables | Boolean | true 時排除任務變數。 |
| variableName         | String  | 指定變數名稱。       |
| variableNameLike     | String  | 變數名稱模糊查詢。     |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 400 | 參數格式錯誤。 |

```json
{
  "data": [
    {
      "id": "14",
      "processInstanceId": "5",
      "processInstanceUrl": "http://localhost:8182/history/historic-process-instances/5",
      "taskId": "6",
      "variable": {
        "name": "myVariable",
        "variableScope": "global",
        "value": "test"
      }
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

#### 進階查詢歷史變數

`POST query/historic-variable-instances`

- Request Body 與 `GET` 參數一致，並可使用 `variables` 陣列進行值篩選。
- 回應碼：200 成功；400 參數錯誤。

#### 取得歷史變數的二進位內容

`GET history/historic-variable-instances/{varInstanceId}/data`

| 回應碼 | 說明                |
|-----|-------------------|
| 200 | 找到變數實例並回傳 binary。 |
| 404 | 變數不存在或無二進位內容。     |

### 歷史明細（Historic Detail）

#### 取得歷史明細清單

`GET history/historic-detail`

| 參數                        | 型別      | 說明             |
|---------------------------|---------|----------------|
| id                        | String  | 指定 detail ID。  |
| processInstanceId         | String  | 指定流程實例。        |
| executionId               | String  | 指定執行 ID。       |
| activityInstanceId        | String  | 指定活動實例。        |
| taskId                    | String  | 指定任務。          |
| selectOnlyFormProperties  | Boolean | true 時僅包含表單屬性。 |
| selectOnlyVariableUpdates | Boolean | true 時僅包含變數更新。 |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 400 | 參數格式錯誤。 |

```json
{
  "data": [
    {
      "id": "26",
      "processInstanceId": "5",
      "processInstanceUrl": "http://localhost:8182/history/historic-process-instances/5",
      "executionId": "6",
      "activityInstanceId": "10",
      "taskId": "6",
      "taskUrl": "http://localhost:8182/history/historic-task-instances/6",
      "time": "2013-04-17T10:17:43.902+0000",
      "detailType": "variableUpdate",
      "revision": 2,
      "variable": {
        "name": "myVariable",
        "variableScope": "global",
        "value": "test"
      },
      "propertyId": null,
      "propertyValue": null
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

#### 進階查詢歷史明細

`POST query/historic-detail`

- Body 欄位與 `GET` 相同，可複合查詢。
- 回應碼：200 成功；400 參數錯誤。

#### 取得歷史明細中的二進位資料

`GET history/historic-detail/{detailId}/data`

| 回應碼 | 說明                       |
|-----|--------------------------|
| 200 | 回傳對應變數的 binary。          |
| 404 | 找不到 detail、變數不存在或無二進位內容。 |

<a id="forms-api"></a>
## 表單（Forms）

### 取得表單欄位資料

`GET form/form-data`

| 參數                  | 必填                          | 型別     | 說明              |
|---------------------|-----------------------------|--------|-----------------|
| taskId              | 是（與 processDefinitionId 擇一） | String | 指定任務對應的表單。      |
| processDefinitionId | 是（與 taskId 擇一）              | String | 查詢流程啟動事件所對應的表單。 |

| 回應碼 | 說明           |
|-----|--------------|
| 200 | 查詢成功並回傳表單資訊。 |
| 404 | 找不到對應的表單。    |

```json
{
  "data": [
    {
      "formKey": null,
      "deploymentId": "2",
      "processDefinitionId": "3",
      "processDefinitionUrl": "http://localhost:8182/repository/process-definition/3",
      "taskId": "6",
      "taskUrl": "http://localhost:8182/runtime/task/6",
      "formProperties": [
        {
          "id": "room",
          "name": "Room",
          "type": "string",
          "value": null,
          "readable": true,
          "writable": true,
          "required": true,
          "datePattern": null,
          "enumValues": [
            {
              "id": "normal",
              "name": "Normal bed"
            },
            {
              "id": "kingsize",
              "name": "Kingsize bed"
            }
          ]
        }
      ]
    }
  ],
  "total": 1,
  "start": 0,
  "sort": "name",
  "order": "asc",
  "size": 1
}
```

### 提交任務或啟動事件表單

`POST form/form-data`

Task Form 範例：

```json
{
  "taskId": "5",
  "properties": [
    {
      "id": "room",
      "value": "normal"
    }
  ]
}
```

Start Event Form 範例：

```json
{
  "processDefinitionId": "5",
  "businessKey": "myKey",
  "properties": [
    {
      "id": "room",
      "value": "normal"
    }
  ]
}
```

| 回應碼 | 說明                                |
|-----|-----------------------------------|
| 200 | 表單送出成功。任務表單無回傳 body；啟動表單會回傳新流程資訊。 |
| 400 | 參數格式錯誤或欄位缺漏。                      |

啟動表單成功回應：

```json
{
  "id": "5",
  "url": "http://localhost:8182/history/historic-process-instances/5",
  "businessKey": "myKey",
  "suspended": false,
  "processDefinitionId": "3",
  "processDefinitionUrl": "http://localhost:8182/repository/process-definition/3",
  "activityId": "myTask"
}
```

<a id="management-runtime-api"></a>
## 管理（Management）與 Runtime

### 資料表 APIs

#### 列出所有資料表

`GET management/tables`

- 回應包含每個表格名稱、URL 與資料筆數。
- 回應碼：200。

#### 取得單一資料表資訊

`GET management/tables/{tableName}`

| 參數        | 必填 | 說明         |
|-----------|----|------------|
| tableName | 是  | 要查詢的資料表名稱。 |

| 回應碼 | 說明         |
|-----|------------|
| 200 | 查詢成功並回傳筆數。 |
| 404 | 指定的資料表不存在。 |

#### 取得資料表欄位資訊

`GET management/tables/{tableName}/columns`

| 回應碼 | 說明         |
|-----|------------|
| 200 | 回傳欄位名稱與型別。 |
| 404 | 找不到指定資料表。  |

```json
{
  "tableName": "ACT_RU_VARIABLE",
  "columnNames": [
    "ID_",
    "REV_",
    "TYPE_",
    "NAME_"
  ],
  "columnTypes": [
    "VARCHAR",
    "INTEGER",
    "VARCHAR",
    "VARCHAR"
  ]
}
```

#### 查詢資料表列資料

`GET management/tables/{tableName}/data`

| Query 參數              | 型別      | 說明          |
|-----------------------|---------|-------------|
| start                 | Integer | 起始索引，預設 0。  |
| size                  | Integer | 回傳筆數，預設 10。 |
| orderAscendingColumn  | String  | 依欄位遞增排序。    |
| orderDescendingColumn | String  | 依欄位遞減排序。    |

| 回應碼 | 說明      |
|-----|---------|
| 200 | 查詢成功。   |
| 404 | 找不到資料表。 |

### 引擎資訊

#### 查詢引擎屬性

`GET management/properties`

- 回傳 Flowable Engine 內部屬性（唯讀）。
- 回應碼：200。

#### 查詢引擎狀態

`GET management/engine`

- 回傳目前 REST 服務所連線的 Engine 名稱、版本、設定檔路徑與例外資訊。
- 回應碼：200。

### Runtime 訊號

#### 廣播訊號事件

`POST runtime/signals`

Request Body：

```json
{
  "signalName": "My Signal",
  "tenantId": "execute",
  "async": true,
  "variables": [
    {
      "name": "testVar",
      "value": "This is a string"
    }
  ]
}
```

| 參數         | 必填 | 說明                                          |
|------------|----|---------------------------------------------|
| signalName | 是  | 訊號名稱。                                       |
| tenantId   | 否  | 指定租戶內的訊號。                                   |
| async      | 否  | true 表示將訊號排入 Job 非同步執行（回傳 202）；false 則立即執行。 |
| variables  | 否  | 一組一般變數格式的 payload，僅可在同步模式使用。                |

| 回應碼 | 說明                                     |
|-----|----------------------------------------|
| 200 | 訊號已立即處理。                               |
| 202 | 訊號已排程等待執行。                             |
| 400 | 缺少 signalName、或 async 模式同時傳 variables。 |

### 工作（Jobs）

#### 取得單一工作

`GET management/jobs/{jobId}`

| 回應碼 | 說明         |
|-----|------------|
| 200 | 找到工作並回傳資訊。 |
| 404 | 指定工作不存在。   |

#### 刪除工作

`DELETE management/jobs/{jobId}`

| 回應碼 | 說明     |
|-----|--------|
| 204 | 刪除成功。  |
| 404 | 找不到工作。 |

#### 立即執行工作

`POST management/jobs/{jobId}`，Body：`{"action": "execute"}`

| 回應碼 | 說明                         |
|-----|----------------------------|
| 204 | 工作已執行。                     |
| 404 | 找不到工作。                     |
| 500 | 執行過程發生例外，可稍後查詢 stacktrace。 |

#### 取得工作例外堆疊

`GET management/jobs/{jobId}/exception-stacktrace`

- 回傳 text/plain 格式 stacktrace。
- 回應碼：200 成功；404 查無工作或無 stacktrace。

#### 查詢工作清單

`GET management/jobs`

| Query 參數                  | 型別      | 說明                                                               |
|---------------------------|---------|------------------------------------------------------------------|
| id                        | String  | 指定工作 ID。                                                         |
| processInstanceId         | String  | 依流程實例篩選。                                                         |
| executionId               | String  | 指定 Execution。                                                    |
| processDefinitionId       | String  | 指定流程定義。                                                          |
| withRetriesLeft           | Boolean | 僅回傳仍有 retries 的工作。                                               |
| executable                | Boolean | true 時僅回傳可立即執行的工作。                                               |
| timersOnly / messagesOnly | Boolean | 僅回傳定時器或訊息工作（兩者不可同時 true）。                                        |
| withException             | Boolean | 僅回傳執行時發生例外者。                                                     |
| dueBefore / dueAfter      | Date    | 依到期時間篩選（無 dueDate 的工作不會被回傳）。                                     |
| exceptionMessage          | String  | 依例外訊息過濾。                                                         |
| tenantId / tenantIdLike   | String  | 指定（模糊）租戶。                                                        |
| withoutTenantId           | Boolean | true 時排除 tenantId。                                               |
| withoutScopeType          | Boolean | true 時僅回傳未設定 scopeType 的工作。                                      |
| sort                      | String  | 排序欄位（id、dueDate、executionId、processInstanceId、retries、tenantId）。 |

| 回應碼 | 說明                                  |
|-----|-------------------------------------|
| 200 | 查詢成功。                               |
| 400 | 參數值非法或同時指定 timersOnly、messagesOnly。 |

```json
{
  "data": [
    {
      "id": "13",
      "url": "http://localhost:8182/management/jobs/13",
      "processInstanceId": "5",
      "processInstanceUrl": "http://localhost:8182/runtime/process-instances/5",
      "processDefinitionId": "timerProcess:1:4",
      "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/timerProcess%3A1%3A4",
      "executionId": "12",
      "executionUrl": "http://localhost:8182/runtime/executions/12",
      "retries": 0,
      "exceptionMessage": "Can't find scripting engine for 'unexistinglanguage'",
      "dueDate": "2013-06-07T10:00:24.653+0000",
      "tenantId": null
    }
  ],
  "total": 2,
  "start": 0,
  "sort": "id",
  "order": "asc",
  "size": 2
}
```

### Deadletter Jobs

#### 取得單一 Deadletter Job

`GET management/deadletter-jobs/{jobId}`

| 回應碼 | 說明                 |
|-----|--------------------|
| 200 | 找到 deadletter job。 |
| 404 | 查無資料。              |

#### 刪除 Deadletter Job

`DELETE management/deadletter-jobs/{jobId}`

| 回應碼 | 說明       |
|-----|----------|
| 204 | 刪除成功。    |
| 404 | 找不到 job。 |

#### 將 Deadletter Job 復原並執行

`POST management/deadletter-jobs/{jobId}`，Body：`{"action": "move"}`

| 回應碼 | 說明                        |
|-----|---------------------------|
| 204 | Job 已移回佇列並執行。             |
| 404 | 找不到 job。                  |
| 500 | 執行時發生錯誤，可透過堆疊 API 取得詳細資訊。 |

#### 取得 Deadletter Job 例外堆疊

`GET management/deadletter-jobs/{jobId}/exception-stacktrace`

- 回應為 text/plain；200 表示成功回傳堆疊，404 表示不存在或無堆疊。

#### 查詢 Deadletter Job 清單

`GET management/deadletter-jobs`

| Query 參數                  | 型別      | 說明                                                               |
|---------------------------|---------|------------------------------------------------------------------|
| id                        | String  | 指定工作 ID。                                                         |
| processInstanceId         | String  | 指定流程實例。                                                          |
| executionId               | String  | 指定 Execution。                                                    |
| processDefinitionId       | String  | 指定流程定義。                                                          |
| executable                | Boolean | 僅回傳可執行的 deadletter job。                                          |
| timersOnly / messagesOnly | Boolean | 僅回傳定時器或訊息型 job（不可同時 true）。                                       |
| withException             | Boolean | 僅回傳有例外的 job。                                                     |
| dueBefore / dueAfter      | Date    | 依到期日篩選（無到期日不會回傳）。                                                |
| tenantId / tenantIdLike   | String  | 指定（模糊）租戶。                                                        |
| withoutTenantId           | Boolean | true 時排除 tenantId。                                               |
| locked / unlocked         | Boolean | 依鎖定狀態篩選。                                                         |
| withoutScopeType          | Boolean | true 時僅回傳無 scopeType。                                            |
| sort                      | String  | 排序欄位（id、dueDate、executionId、processInstanceId、retries、tenantId）。 |

| 回應碼 | 說明                                  |
|-----|-------------------------------------|
| 200 | 查詢成功。                               |
| 400 | 參數非法或 timersOnly、messagesOnly 同時使用。 |

```json
{
  "data": [
    {
      "id": "13",
      "url": "http://localhost:8182/management/jobs/13",
      "processInstanceId": "5",
      "processInstanceUrl": "http://localhost:8182/runtime/process-instances/5",
      "processDefinitionId": "timerProcess:1:4",
      "processDefinitionUrl": "http://localhost:8182/repository/process-definitions/timerProcess%3A1%3A4",
      "executionId": "12",
      "executionUrl": "http://localhost:8182/runtime/executions/12",
      "retries": 0,
      "exceptionMessage": "Can't find scripting engine for 'unexistinglanguage'",
      "dueDate": "2013-06-07T10:00:24.653+0000",
      "createTime": "2013-06-06T08:08:24.007+0000",
      "tenantId": null
    }
  ],
  "total": 2,
  "start": 0,
  "sort": "id",
  "order": "asc",
  "size": 2
}
```

<a id="identity-api"></a>
## 身分管理（Identity）

### 使用者（Users）

#### 取得單一使用者

`GET identity/users/{userId}`

| 回應碼 | 說明          |
|-----|-------------|
| 200 | 使用者存在並回傳資料。 |
| 404 | 查無此使用者。     |

#### 查詢使用者清單

`GET identity/users`

| Query 參數                                 | 說明                                 |
|------------------------------------------|------------------------------------|
| id                                       | 依使用者 ID。                           |
| firstName / lastName / email             | 完全比對名字、姓氏、Email。                   |
| firstNameLike / lastNameLike / emailLike | 模糊比對（% 為萬用字元）。                     |
| memberOfGroup                            | 僅回傳隸屬於指定群組的使用者。                    |
| potentialStarter                         | 僅回傳可啟動指定流程定義的使用者。                  |
| sort                                     | 排序欄位（id、firstName、lastName、email）。 |

| 回應碼 | 說明    |
|-----|-------|
| 200 | 查詢成功。 |

```json
{
  "data": [
    {
      "id": "anotherUser",
      "firstName": "Tijs",
      "lastName": "Barrez",
      "url": "http://localhost:8182/identity/users/anotherUser",
      "email": "no-reply@flowable.org"
    },
    {
      "id": "kermit",
      "firstName": "Kermit",
      "lastName": "the Frog",
      "url": "http://localhost:8182/identity/users/kermit",
      "email": null
    }
  ],
  "total": 3,
  "start": 0,
  "sort": "id",
  "order": "asc",
  "size": 3
}
```

#### 更新使用者

`PUT identity/users/{userId}`

Request Body：

```json
{
  "firstName": "Tijs",
  "lastName": "Barrez",
  "email": "no-reply@flowable.org",
  "password": "pass123"
}
```

- 欄位皆為選填；若明確給 null 則會清空欄位。
- 回應碼：200 更新成功；404 找不到使用者；409 代表同時有其他更新。

#### 建立使用者

`POST identity/users`

```json
{
  "id": "tijs",
  "firstName": "Tijs",
  "lastName": "Barrez",
  "email": "no-reply@flowable.org",
  "password": "pass123"
}
```

| 回應碼 | 說明     |
|-----|--------|
| 201 | 建立成功。  |
| 400 | 缺少 id。 |

#### 刪除使用者

`DELETE identity/users/{userId}`

| 回應碼 | 說明      |
|-----|---------|
| 204 | 刪除成功。   |
| 404 | 找不到使用者。 |

#### 取得使用者頭像

`GET identity/users/{userId}/picture`

- 回傳二進位圖檔；Content-Type 依建立時設定。
- 回應碼：200 成功；404 無此人或未設定圖片。

#### 更新使用者頭像

`PUT identity/users/{userId}/picture`

- 使用 `multipart/form-data`，檔案欄位為 `file`；可額外提供 `mimeType`（預設 image/jpeg）。
- 回應碼：200 成功；404 找不到使用者。

### 使用者自訂資訊（User Info）

#### 列出所有自訂資訊鍵

`GET identity/users/{userId}/info`

| 回應碼 | 說明                |
|-----|-------------------|
| 200 | 回傳所有 key 與對應 URL。 |
| 404 | 找不到使用者。           |

```json
[
  {
    "key": "key1",
    "url": "http://localhost:8182/identity/users/testuser/info/key1"
  },
  {
    "key": "key2",
    "url": "http://localhost:8182/identity/users/testuser/info/key2"
  }
]
```

#### 取得指定 key 的資訊

`GET identity/users/{userId}/info/{key}`

| 回應碼 | 說明               |
|-----|------------------|
| 200 | 找到並回傳 key/value。 |
| 404 | 找不到使用者或該 key。    |

#### 更新指定 key

`PUT identity/users/{userId}/info/{key}`

Request Body：`{ "value": "The updated value" }`

| 回應碼 | 說明            |
|-----|---------------|
| 200 | 更新成功。         |
| 400 | 缺少 value。     |
| 404 | 找不到使用者或該 key。 |

#### 新增使用者自訂資訊

`POST identity/users/{userId}/info`

Request Body：`{ "key": "key1", "value": "The value" }`

| 回應碼 | 說明               |
|-----|------------------|
| 201 | 建立成功並回傳內容。       |
| 400 | 缺少 key 或 value。  |
| 404 | 找不到使用者。          |
| 409 | key 已存在，應改用 PUT。 |

#### 刪除指定 key

`DELETE identity/users/{userId}/info/{key}`

| 回應碼 | 說明            |
|-----|---------------|
| 204 | 刪除成功。         |
| 404 | 找不到使用者或該 key。 |

### 群組（Groups）

#### 取得單一群組

`GET identity/groups/{groupId}`

| 回應碼 | 說明         |
|-----|------------|
| 200 | 群組存在並回傳資料。 |
| 404 | 群組不存在。     |

#### 查詢群組清單

`GET identity/groups`

| Query 參數         | 說明                  |
|------------------|---------------------|
| id               | 依群組 ID。             |
| name / nameLike  | 依名稱或模糊名稱。           |
| type             | 依群組型別。              |
| member           | 僅回傳含指定使用者的群組。       |
| potentialStarter | 僅回傳該群組成員可啟動指定流程的群組。 |
| sort             | 排序欄位（id、name、type）。 |

| 回應碼 | 說明    |
|-----|-------|
| 200 | 查詢成功。 |

#### 更新群組

`PUT identity/groups/{groupId}`

```json
{
  "name": "Test group",
  "type": "Test type"
}
```

- 欄位皆選填，提供 null 會清空欄位。
- 回應碼：200 更新成功；404 群組不存在；409 有其他同步更新。

#### 建立群組

`POST identity/groups`

```json
{
  "id": "testgroup",
  "name": "Test group",
  "type": "Test type"
}
```

| 回應碼 | 說明     |
|-----|--------|
| 201 | 建立成功。  |
| 400 | 缺少 id。 |

#### 刪除群組

`DELETE identity/groups/{groupId}`

| 回應碼 | 說明    |
|-----|-------|
| 204 | 刪除成功。 |
| 404 | 查無群組。 |

#### 查詢群組成員

- `identity/groups/members` 不支援 GET。
- 請改用 `identity/users?memberOfGroup={groupId}` 取得群組所有使用者。

#### 新增群組成員

`POST identity/groups/{groupId}/members`

Request Body：`{ "userId": "kermit" }`

| 回應碼 | 說明                     |
|-----|------------------------|
| 201 | 加入成功並回傳成員資訊。           |
| 404 | Body 缺少 userId 或找不到群組。 |
| 409 | 該使用者已是群組成員。            |

```json
{
  "userId": "kermit",
  "groupId": "sales",
  "url": "http://localhost:8182/identity/groups/sales/members/kermit"
}
```

#### 移除群組成員

`DELETE identity/groups/{groupId}/members/{userId}`

| 回應碼 | 說明                |
|-----|-------------------|
| 204 | 成功移除。             |
| 404 | 群組不存在或使用者不是該群組成員。 |
