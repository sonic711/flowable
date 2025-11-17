package com.bot.fsap.flowable.process.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfiguration1 {

    @Value("${spring.datasource.url}")
    String dbUrl;
    @Value("${spring.datasource.driver-class-name}")
    String dbDriver;

	@Value("${spring.datasource.username}")
    String databaseUsername;

	@Value("${spring.datasource.password}")
    String databasePassword;

    @Bean
    DataSource getDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(dbUrl);
        dataSource.setDriverClassName(dbDriver);
        dataSource.setUsername(databaseUsername);
        dataSource.setPassword(databasePassword);
        // HikariCP設定，例如最大連線池大小
        dataSource.setMaximumPoolSize(50);         // 連線池的最大連線數量
        // dataSource.setConnectionTimeout(60000); // 等待連線的最大時間
        dataSource.setMinimumIdle(5);              // 最少閒置連線數
        dataSource.setIdleTimeout(600000);         // 10 分鐘
        dataSource.setMaxLifetime(1800000);        // 30 分鐘
        return dataSource;
    }
}
