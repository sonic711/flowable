package com.bot.fsap.flowable.web.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.bot.fsap.flowable.web.filter.DynamicCharsetFilter;

/**
 * Filter 註冊配置
 */
@Configuration
public class FilterConfig {

	@Bean
	public FilterRegistrationBean<DynamicCharsetFilter> dynamicCharsetFilter() {
		FilterRegistrationBean<DynamicCharsetFilter> registrationBean = new FilterRegistrationBean<>();

		registrationBean.setFilter(new DynamicCharsetFilter());

		// 設定 URL patterns，/* 表示所有請求
		registrationBean.addUrlPatterns("/*");

		// 設定順序，必須在 Spring 預設的 CharacterEncodingFilter 之前
		// Spring 的 CharacterEncodingFilter 順序是 Ordered.HIGHEST_PRECEDENCE
		registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE - 1);

		// Filter 名稱
		registrationBean.setName("dynamicCharsetFilter");

		return registrationBean;
	}
}
