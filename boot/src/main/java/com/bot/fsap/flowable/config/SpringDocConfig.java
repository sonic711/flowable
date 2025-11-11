package com.bot.fsap.flowable.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2025 All Rights Reserved. <br>
 * 日期：2025/11/11<br>
 * 作者：Sean Liu<br>
 * 程式代號: SpringDocConfig.java<br>
 * 程式說明: <br>
 * ======================================================================
 */
@OpenAPIDefinition
@Configuration
public class SpringDocConfig {

	@Bean
	public GroupedOpenApi flowableApi() {
		return GroupedOpenApi.builder()
				.group("flowable")
				.pathsToMatch("/flowable-rest/**")
				.addOperationCustomizer((operation, handlerMethod) -> {
					// 自訂 Flowable 端點的描述（範例：任務端點）
					if (handlerMethod.getMethod().getName().contains("tasks")) {
						operation.addTagsItem("Flowable Tasks");
					}
					return operation;
				})
				.build();
	}

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("Flowable REST API").version("1.0.0"));
	}
}
