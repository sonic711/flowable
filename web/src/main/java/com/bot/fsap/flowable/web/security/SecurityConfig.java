package com.bot.fsap.flowable.web.security;

import java.time.Instant;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2025 All Rights Reserved. <br>
 * 日期：2025/11/17<br>
 * 作者：Sean Liu<br>
 * 程式代號: SecurityConfig.java<br>
 * 程式說明: <br>
 * ======================================================================
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	// 第一條：專門給 Swagger/Actuator 用（完全不走 Security 的 session 限制）
	@Order(1)
	@Bean
	public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher(//
						"/v3/api-docs/**",//
						"/swagger-ui.html",//
						"/swagger-ui/**",//
						"/swagger-resources/**",//
						"/webjars/**",//
						"/actuator/**")//
				.csrf(AbstractHttpConfigurer::disable)//
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())//
				// 關鍵：這條 chain 完全不設定 sessionManagement，讓 Undertow 用預設行為（允許 session）
				.sessionManagement(AbstractHttpConfigurer::disable);

		return http.build();
	}

	// 第二條：所有真正的 API（Flowable IDM 等）走真正的無狀態 Basic Auth
	@Order(2)
	@Bean
	public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)//
				.httpBasic(Customizer.withDefaults())//
				.formLogin(AbstractHttpConfigurer::disable)//
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))//
				.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())//
				.exceptionHandling(exception -> exception//
						.authenticationEntryPoint((req, res, ex) -> {//
							res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);//
							res.setContentType("application/json;charset=UTF-8");//
							res.getWriter().write("""
									{
									  "timestamp": "%s",
									  "status": 401,
									  "error": "Unauthorized",
									  "message": "需要認證才能存取此資源",
									  "path": "%s"
									}
									""".formatted(Instant.now().toString(), req.getRequestURI()));
						}));

		return http.build();
	}
}
