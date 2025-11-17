package com.bot.fsap.flowable.process.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

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

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)  // 保持關閉，解決 POST/DELETE 問題
				// 啟用 Basic Auth（這行很重要！如果沒加，Basic Auth 不會觸發）
				.httpBasic(Customizer.withDefaults())  // 或 .httpBasic(httpBasic -> httpBasic.realmName("Flowable IDM"))
				// 關掉 form login（如果不用傳統登入頁）
				.formLogin(AbstractHttpConfigurer::disable)
				// Session 設定：Flowable IDM 預設用 session，設 ALWAYS 以確保 Basic Auth 能存 session
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.ALWAYS));

		return http.build();
	}
}
