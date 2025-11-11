package com.bot.fsap.flowable.web.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.bot.fsap.flowable.web.converter.DynamicCharsetHttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Web MVC 配置
 * 註冊自訂的 HttpMessageConverter
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

	private final ObjectMapper objectMapper;

	public WebMvcConfig(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
		// 移除預設的 MappingJackson2HttpMessageConverter
		converters.removeIf(converter -> converter.getClass().getSimpleName().equals("MappingJackson2HttpMessageConverter"));

		// 加入我們自訂的 Converter
		converters.add(new DynamicCharsetHttpMessageConverter(objectMapper));
	}
}
