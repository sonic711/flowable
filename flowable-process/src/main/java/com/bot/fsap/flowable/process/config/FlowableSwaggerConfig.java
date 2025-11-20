package com.bot.fsap.flowable.process.config;

import org.flowable.rest.service.api.RestResponseFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

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

	@Bean
	public RestResponseFactory restResponseFactory(ObjectMapper objectMapper) {
		return new RestResponseFactory(objectMapper);
	}

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()//
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
