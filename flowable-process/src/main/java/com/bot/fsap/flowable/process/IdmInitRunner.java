package com.bot.fsap.flowable.process;

import org.flowable.idm.api.IdmIdentityService;
import org.flowable.idm.api.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ====================================================================== <br>
 * Licensed Materials - Property of BlueTechnology Corp., Ltd. <br>
 * 藍科數位科技股份有限公司版權所有翻印必究 <br>
 * (C) Copyright BlueTechnology Corp., Ltd. 2025 All Rights Reserved. <br>
 * 日期：2025/11/13<br>
 * 作者：Sean Liu<br>
 * 程式代號: IdmInitRunner.java<br>
 * 程式說明: <br>
 * ======================================================================
 */
@Configuration
public class IdmInitRunner {

	@Autowired
	private IdmIdentityService idmIdentityService;

//	@Bean
	public CommandLineRunner initUsers() {
		return args -> {
			// 檢查並建立 admin 使用者
			User existingAdmin = idmIdentityService.createUserQuery().userId("admin").singleResult();
			if (existingAdmin == null) {
				User admin = idmIdentityService.newUser("admin");
				admin.setPassword("test");
				admin.setFirstName("Admin");
				admin.setLastName("User");
				admin.setEmail("admin@example.com");
				idmIdentityService.saveUser(admin);
				System.out.println("✅ Admin user created - Username: admin, Password: test");
			} else {
				System.out.println("ℹ️ Admin user already exists");
			}

			// 檢查並建立一般使用者
			User existingUser = idmIdentityService.createUserQuery().userId("user").singleResult();
			if (existingUser == null) {
				User user = idmIdentityService.newUser("user");
				user.setPassword("user");
				user.setFirstName("Test");
				user.setLastName("User");
				user.setEmail("user@example.com");
				idmIdentityService.saveUser(user);
				System.out.println("✅ Test user created - Username: user, Password: user");
			}

			// === 建立 admin 群組 (ID 必須是 "admin") ===
			if (idmIdentityService.createGroupQuery().groupId("admin").singleResult() == null) {
				org.flowable.idm.api.Group adminGroup = idmIdentityService.newGroup("admin");
				adminGroup.setName("Administrators");
				adminGroup.setType("security-role");
				idmIdentityService.saveGroup(adminGroup);

				idmIdentityService.createMembership("admin", "admin");
				System.out.println("✅ Admin group created and admin user added");
			}

			// === 建立 user 群組 (ID 必須是 "user"，不是 "users") ===
			if (idmIdentityService.createGroupQuery().groupId("user").singleResult() == null) {
				org.flowable.idm.api.Group userGroup = idmIdentityService.newGroup("user");
				userGroup.setName("Users");
				userGroup.setType("security-role");
				idmIdentityService.saveGroup(userGroup);

				idmIdentityService.createMembership("user", "user");  // 注意：userId, groupId
				System.out.println("✅ User group created and test user added");
			}
		};
	}
}
