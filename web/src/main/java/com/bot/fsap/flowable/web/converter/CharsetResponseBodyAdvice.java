package com.bot.fsap.flowable.web.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 統一處理回應編碼
 * 確保回應的編碼與請求的編碼一致
 */
@ControllerAdvice
public class CharsetResponseBodyAdvice implements ResponseBodyAdvice<Object> {

	private static final Logger logger = LoggerFactory.getLogger(CharsetResponseBodyAdvice.class);
	private static final String CHARSET_ATTRIBUTE = "requestCharset";

	@Override
	public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
		// 對所有回應都套用
		return true;
	}

	@Override
	public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {

		if (request instanceof ServletServerHttpRequest && response instanceof ServletServerHttpResponse) {
			HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
			HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();

			// 從 request attribute 取得編碼（由 Filter 設定）
			String charset = (String) servletRequest.getAttribute(CHARSET_ATTRIBUTE);

			if (charset != null) {
				// 設定 Content-Type 包含 charset
				String contentType = selectedContentType.toString();
				if (!contentType.contains("charset")) {
					contentType = contentType + "; charset=" + charset;
					servletResponse.setContentType(contentType);
					logger.debug("Set response Content-Type to: {}", contentType);
				}
			}
		}

		return body;
	}
}
