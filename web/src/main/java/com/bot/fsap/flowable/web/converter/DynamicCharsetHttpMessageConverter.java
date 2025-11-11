package com.bot.fsap.flowable.web.converter;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;

/**
 * 自訂 JSON Message Converter
 * 根據請求的編碼動態設定回應的編碼
 */
public class DynamicCharsetHttpMessageConverter extends MappingJackson2HttpMessageConverter {

	private static final Logger logger = LoggerFactory.getLogger(DynamicCharsetHttpMessageConverter.class);
	private static final String CHARSET_ATTRIBUTE = "requestCharset";
	private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	public DynamicCharsetHttpMessageConverter(ObjectMapper objectMapper) {
		super(objectMapper);
	}

	@Override
	@NonNull
	public Object read(@NonNull Type type, Class<?> contextClass, @NonNull HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {

		// 讀取時使用父類的邏輯（編碼已在 Filter 中設定）
		return super.read(type, contextClass, inputMessage);
	}

	@Override
	protected void writeInternal(@NonNull Object object, Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {

		// 取得當前請求的編碼
		Charset charset = getRequestCharset();

		// 設定回應的 Content-Type 包含正確的 charset
		MediaType contentType = outputMessage.getHeaders().getContentType();
		if (contentType != null) {
			MediaType newContentType = new MediaType(contentType.getType(), contentType.getSubtype(), charset);
			outputMessage.getHeaders().setContentType(newContentType);
			logger.debug("Set response Content-Type to: {}", newContentType);
		}

		// 設定 ObjectMapper 使用指定的編碼
		// 注意：需要用 JsonGenerator 來控制輸出編碼
		ObjectMapper mapper = getObjectMapper();

		try {
			// 使用指定編碼寫入
			byte[] jsonBytes = mapper.writeValueAsString(object).getBytes(charset);
			outputMessage.getBody().write(jsonBytes);
			outputMessage.getBody().flush();
		} catch (IOException e) {
			logger.error("Failed to write response with charset: {}", charset, e);
			throw e;
		}
	}

	/**
	 * 從 Request Attribute 取得編碼
	 */
	private Charset getRequestCharset() {
		try {
			ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

			if (attributes != null) {
				HttpServletRequest request = attributes.getRequest();
				String charsetName = (String) request.getAttribute(CHARSET_ATTRIBUTE);

				if (charsetName != null) {
					return Charset.forName(charsetName);
				}
			}
		} catch (Exception e) {
			logger.warn("Failed to get request charset, using default", e);
		}

		return DEFAULT_CHARSET;
	}
}
