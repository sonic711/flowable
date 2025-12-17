package com.bot.fsap.flowable.process.config;

import java.util.List;

import org.flowable.rest.service.api.RestResponseFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2025 All Rights Reserved. <br>
 * 日期：2025/11/20<br>
 * 作者：Sean Liu<br>
 * 程式代號: FlowableSwaggerConfig.java<br>
 * 程式說明: <br>
 * ======================================================================
 */
@Configuration
@ComponentScan(basePackages = { "org.flowable.rest.service.api" })
public class FlowableSwaggerConfig {

	@Value("${server.port}")
	private String port;

	@Bean
	public RestResponseFactory restResponseFactory(ObjectMapper objectMapper) {
		return new RestResponseFactory(objectMapper);
	}

	@Bean
	public OpenAPI customOpenAPI() {
		String flowableDescription = """
					Flowable 群組說明：
					- Flowable-Definition：以 Flowable Repository REST 為主，提供流程定義、部署與模型（Modeler）查詢，URI 會被自動補成 `/process-api/repository/**`。
					- Flowable-Runtime：對應 Runtime REST，涵蓋流程執行、待辦任務、執行緒與流程變數，實際路徑為 `/process-api/runtime/**`。
					- Flowable-History：查詢歷史流程與任務資料，包括變數快照與活動紀錄，路徑 `/process-api/history/**`。
					- Flowable-Management：引擎維運 API，例如作業排程、死信、資料表狀態與引擎屬性，以 `/process-api/management/**` 提供。
					- Flowable-Identity：使用者、群組與 IDM 延伸 API，啟用 IDM Starter 後可透過 `/process-api/identity/**` 或 `/process-api/idm-api/**` 管理帳號。
					- My Application：專供自訂 Controller，預設排除 Flowable 官方 package，可逐步納入企業 API（無 `/process-api` 前綴）。
					Flowable 平台 Swagger UI 使用說明：
					1. 入口：`/swagger-ui/index.html`，Flowable REST 會自動補上 `/process-api` 前綴，My Application group 則維持實際路徑。
					2. 認證：按下 `Authorize` 後輸入 basic auth 帳密（預設 `rest-admin / test`，可在 `application-flowable.yml` 覆寫）。
					3. 呼叫流程：展開 operation → `Try it out` → 填參數 → `Execute`，Swagger 會帶入 Authorization header，並由 `RequestResponseLoggingFilter` 記錄到 `${BASE_PATH}/flowable_http_exchange.log`。
					4. 更多細節請見 `README.md` 的「Swagger 頁面使用說明」。
				""";

		List<Server> servers = List.of(//
				new Server().url("http://localhost:" + port)//
						.description("LOCAL，供開發用"),//
				new Server().url("http://172.17.24.79:" + port)//
						.description("DEV（flowuser@172.17.24.79:/app/fsap/flowable），供整合測試之用"));

		return new OpenAPI()//
				.info(new Info()//
						.title("FSAP Flowable Process API")//
						.summary("Flowable REST 與 FSAP 自訂 API 的統一入口")//
						.version("2025.11")//
						.description(flowableDescription)//
						.contact(new Contact().name("Flowable Platform Team")//
								.url("https://www.bluetechnology.com.tw"))//
						.license(new License().name("BlueTechnology Internal Use Only")//
								.url("https://www.bluetechnology.com.tw/license")))//
				.servers(servers)//
				// 1. 定義認證機制 (Components)
				.components(new Components()//
						.addSecuritySchemes("basicAuth", // 這個名稱可以自訂
								new SecurityScheme()//
										.type(SecurityScheme.Type.HTTP) // 類型是 HTTP
										.scheme("basic") // 方案是 basic (對應您的 SecurityConfig)
						))
				// 2. 套用到全域 (SecurityRequirement)
				// 這樣 Swagger 裡面的每一個 API 預設都會有一把鎖，並帶入上面定義的 basicAuth
				.addSecurityItem(new SecurityRequirement().addList("basicAuth"));
	}

	// ==========================================
	//      Flowable API 分類設定 (拆成多個 Group)
	// ==========================================

	/**
	 * 群組 1: 流程定義與部署 (Repository)
	 * 包含: Process Definitions, Deployments, Models
	 */
	@Bean
	public GroupedOpenApi flowableRepositoryApi() {
		return buildFlowableGroup("Flowable-Definition", "/repository/**");
	}

	/**
	 * 群組 2: 執行期間 (Runtime)
	 * 包含: Process Instances, Tasks, Executions, Variables
	 */
	@Bean
	public GroupedOpenApi flowableRuntimeApi() {
		return buildFlowableGroup("Flowable-Runtime", "/runtime/**");
	}

	/**
	 * 群組 3: 歷史紀錄 (History)
	 * 包含: Historic Process Instances, Historic Task Instances
	 */
	@Bean
	public GroupedOpenApi flowableHistoryApi() {
		return buildFlowableGroup("Flowable-History", "/history/**");
	}

	/**
	 * 群組 4: 管理與維運 (Management)
	 * 包含: Jobs, Deadletter Jobs, Engine Properties, Database tables
	 */
	@Bean
	public GroupedOpenApi flowableManagementApi() {
		return buildFlowableGroup("Flowable-Management", "/management/**");
	}

	/**
	 * 群組 5: 身份識別 (Identity) - 如果您有開啟 IDM
	 * 包含: Users, Groups
	 */
	@Bean
	public GroupedOpenApi flowableIdentityApi() {
		// 舊版可能是 /identity/**，新版 Spring Boot Starter 預設有時不包含 IDM REST，視您的依賴而定
		return buildFlowableGroup("Flowable-Identity", "/identity/**", "/idm-api/**");
	}

	// ==========================================
	//      Helper Method (避免重複代碼)
	// ==========================================

	/**
	 * 建構 Flowable Group 的輔助方法
	 * 自動加上路徑過濾與 /process-api 前綴
	 */
	private GroupedOpenApi buildFlowableGroup(String groupName, String... pathsToMatch) {
		return GroupedOpenApi.builder()//
				.group(groupName)//
				.pathsToMatch(pathsToMatch) // 這裡用原始路徑 (例如 /repository/**)
				.addOpenApiCustomizer(openApi -> {
					// 這裡負責把掃描到的路徑加上 /process-api 前綴
					Paths oldPaths = openApi.getPaths();
					Paths newPaths = new Paths();

					if (oldPaths != null) {
						oldPaths.forEach((path, pathItem) -> {
							// 確保不重複加前綴
							String newPath = path.startsWith("/process-api") ? path : "/process-api" + path;
							newPaths.addPathItem(newPath, pathItem);
						});
					}
					openApi.setPaths(newPaths);
				})//
				.build();
	}

	// 明確定義自己的 API Group，以免混淆
	@Bean
	public GroupedOpenApi myApplicationApi() {
		return GroupedOpenApi.builder()//
				.group("My Application")//
				.pathsToMatch("/**") // API 路徑規則，目前無前綴
				.packagesToExclude("org.flowable.rest.service.api")// 排除flowaple api
				// .packagesToScan("com.bot.fsap.flowable") // 或是用 package 掃描自己的套件
				.build();
	}
}
