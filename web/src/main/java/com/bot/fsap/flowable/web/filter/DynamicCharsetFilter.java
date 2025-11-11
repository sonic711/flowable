package com.bot.fsap.flowable.web.filter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 根據 Content-Type 的 charset 動態設定請求編碼
 * 支援 UTF-8 和 BIG5 編碼
 */
public class DynamicCharsetFilter implements Filter {

	private static final Logger logger = LoggerFactory.getLogger(DynamicCharsetFilter.class);
	private static final String DEFAULT_CHARSET = "UTF-8";

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

		HttpServletRequest httpRequest = (HttpServletRequest) request;

		// 取得 Content-Type
		String contentType = httpRequest.getContentType();
		String charset = DEFAULT_CHARSET; // 預設使用 UTF-8

		if (contentType != null && !contentType.isEmpty()) {
			// 解析 charset
			String extractedCharset = extractCharset(contentType);

			if (extractedCharset != null) {
				try {
					// 驗證 charset 是否有效
					Charset.forName(extractedCharset);
					charset = extractedCharset;
					logger.debug("Detected charset: {}", charset);

				} catch (UnsupportedCharsetException e) {
					logger.warn("Unsupported charset: {}, using default: {}", extractedCharset, DEFAULT_CHARSET);
				}
			}
		}

		// 設定請求編碼
		request.setCharacterEncoding(charset);

		// 設定回應編碼（與請求相同）
		response.setCharacterEncoding(charset);

		// 將 charset 儲存到 request attribute，供 Controller 使用
		httpRequest.setAttribute("requestCharset", charset);

		logger.debug("Set request and response charset to: {}", charset);

		// 繼續 filter chain
		chain.doFilter(request, response);
	}

	@Override
	public void destroy() {
		logger.info("DynamicCharsetFilter destroyed");
	}

	/**
	 * 從 Content-Type 中提取 charset
	 * 例如: "application/json; charset=BIG5" -> "BIG5"
	 */
	private String extractCharset(String contentType) {
		if (contentType == null) {
			return null;
		}

		// 尋找 charset= 的位置
		int charsetIndex = contentType.toLowerCase().indexOf("charset=");
		if (charsetIndex == -1) {
			return null;
		}

		// 提取 charset 值
		String charsetPart = contentType.substring(charsetIndex + 8); // "charset=" 長度為 8

		// 移除分號後的其他參數
		int semicolonIndex = charsetPart.indexOf(';');
		if (semicolonIndex != -1) {
			charsetPart = charsetPart.substring(0, semicolonIndex);
		}

		// 去除空白和引號
		charsetPart = charsetPart.trim().replace("\"", "");

		// 正規化編碼名稱
		return normalizeCharsetName(charsetPart);
	}

	/**
	 * 正規化 charset 名稱
	 * 處理大小寫和常見變體
	 */
	private String normalizeCharsetName(String charset) {
		if (charset == null || charset.isEmpty()) {
			return null;
		}

		String upperCharset = charset.toUpperCase();

		// 處理常見的編碼名稱變體
		return switch (upperCharset) {
			case "UTF-8", "UTF8" -> "UTF-8";
			case "BIG5", "BIG-5" -> "Big5"; // Java 標準名稱
			case "GB2312", "GBK" -> upperCharset;
			default -> charset; // 保持原樣
		};
	}
}
