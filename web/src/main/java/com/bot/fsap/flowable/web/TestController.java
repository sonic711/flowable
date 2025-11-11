package com.bot.fsap.flowable.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 測試 Controller
 * 用於驗證 UTF-8 和 BIG5 編碼是否正確處理
 */
@RestController
@RequestMapping("/api")
public class TestController {

	private static final Logger logger = LoggerFactory.getLogger(TestController.class);

	/**
	 * 接收 JSON 資料
	 * 前端需在 Content-Type 中指定 charset
	 * 例如: Content-Type: application/json; charset=BIG5
	 * 回應會使用相同的編碼
	 */
	@PostMapping("/data")
	public Map<String, Object> receiveData(@RequestBody Map<String, Object> data, @RequestAttribute(name = "requestCharset", required = false) String charset) {

		logger.info("Received data: {}, charset: {}", data, charset);

		// 直接返回接收到的資料，用於驗證編碼是否正確
		return Map.of("success", true, "receivedData", data, "requestCharset", charset != null ? charset : "UTF-8", "message", "資料接收成功，回應編碼：" + (charset != null ? charset : "UTF-8"));
	}

	/**
	 * 接收物件
	 */
	@PostMapping("/user")
	public UserResponse createUser(@RequestBody UserRequest request) {
		logger.info("Received user: name={}, description={}", request.getName(), request.getDescription());

		UserResponse response = new UserResponse();
		response.setSuccess(true);
		response.setName(request.getName());
		response.setDescription(request.getDescription());
		response.setMessage("使用者建立成功：" + request.getName());

		return response;
	}

	// DTO 類別
	public static class UserRequest {
		private String name;
		private String description;

		public String getName() {return name;}

		public void setName(String name) {this.name = name;}

		public String getDescription() {return description;}

		public void setDescription(String description) {this.description = description;}
	}

	public static class UserResponse {
		private boolean success;
		private String name;
		private String description;
		private String message;

		public boolean isSuccess() {return success;}

		public void setSuccess(boolean success) {this.success = success;}

		public String getName() {return name;}

		public void setName(String name) {this.name = name;}

		public String getDescription() {return description;}

		public void setDescription(String description) {this.description = description;}

		public String getMessage() {return message;}

		public void setMessage(String message) {this.message = message;}
	}
}
