package com.bot.fsap.flowable.process.service;

import java.util.List;

import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.Privilege;
import org.flowable.idm.api.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 確保 Flowable 啟動後存在預設管理者帳號。
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

	private static final Logger logger = LoggerFactory.getLogger(AdminUserInitializer.class);
	private static final List<String> ADMIN_PRIVILEGES = List.of(//
			"access-admin",//
			"access-task",//
			"access-modeler",//
			"access-rest-api"//
	);

	private final IdmIdentityService idmIdentityService;
	private final List<String> adminUserIds;
	private final String adminDefaultPassword;

	public AdminUserInitializer(//
			IdmIdentityService idmIdentityService,//
			@Value("${flowable.admin.users:rest-admin}") List<String> adminUserIds,//
			@Value("${flowable.admin.default-pw:test}") String adminDefaultPassword) {
		this.idmIdentityService = idmIdentityService;
		this.adminUserIds = adminUserIds;
		this.adminDefaultPassword = adminDefaultPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		initializeAdminUsers();
	}

	private void initializeAdminUsers() {
		for (String userId : adminUserIds) {
			if (!StringUtils.hasText(userId)) {
				continue;
			}
			String trimmedUserId = userId.trim();
			createAdminUserIfNecessary(trimmedUserId);
		}
	}

	private void createAdminUserIfNecessary(String userId) {
		User adminUser = idmIdentityService.createUserQuery().userId(userId).singleResult();
		if (adminUser == null) {
			adminUser = idmIdentityService.newUser(userId);
			adminUser.setFirstName("Flowable");
			adminUser.setLastName("Administrator");
			adminUser.setPassword(adminDefaultPassword);
			adminUser.setEmail(String.format("%s@flowable.local", userId));
			idmIdentityService.saveUser(adminUser);
			logger.info("已建立預設管理者帳號：{}/{}", userId, adminDefaultPassword);
		} else {
			logger.info("預設管理者帳號 {} 已存在，跳過建立", userId);
		}

		grantAdminPrivileges(userId);
	}

	private void grantAdminPrivileges(String userId) {
		for (String privilegeName : ADMIN_PRIVILEGES) {
			Privilege privilege = ensurePrivilegeExists(privilegeName);
			long existingMapping = idmIdentityService.createPrivilegeQuery().privilegeId(privilege.getId()).userId(userId).count();
			if (existingMapping == 0L) {
				idmIdentityService.addUserPrivilegeMapping(privilege.getId(), userId);
				logger.info("已授予管理者 {} 權限 {}", userId, privilegeName);
			}
		}
	}

	private Privilege ensurePrivilegeExists(String privilegeName) {
		Privilege privilege = idmIdentityService.createPrivilegeQuery().privilegeName(privilegeName).singleResult();
		if (privilege == null) {
			privilege = idmIdentityService.createPrivilege(privilegeName);
			logger.info("已建立 Flowable 權限 {}", privilegeName);
		}
		return privilege;
	}
}
