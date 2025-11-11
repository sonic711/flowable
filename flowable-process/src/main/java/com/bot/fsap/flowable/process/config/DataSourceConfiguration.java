package com.bot.fsap.flowable.process.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 資料來源配置類別
 *
 * <p>此配置類別負責設定 Flowable 所需的資料來源：</p>
 * <ul>
 *   <li>H2 記憶體資料庫配置（開發/測試環境）</li>
 *   <li>H2 檔案資料庫配置（生產環境）</li>
 *   <li>連接池配置</li>
 *   <li>事務管理配置</li>
 * </ul>
 *
 * @author Blue Flowable Team
 * @since 1.0.0
 */
@Configuration
@EnableTransactionManagement
@Profile({"dev", "test", "prod"}) // 明確指定 Profile，避免與 Flowable 自動配置衝突
public class DataSourceConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfiguration.class);

    @Value("${spring.datasource.url:jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MYSQL}")
    private String databaseUrl;

    @Value("${spring.datasource.username:sa}")
    private String databaseUsername;

    @Value("${spring.datasource.password:}")
    private String databasePassword;

    @Value("${spring.datasource.driver-class-name:org.h2.Driver}")
    private String databaseDriverClassName;

    /**
     * 開發和測試環境的 H2 記憶體資料庫配置
     *
     * @return DataSource H2 記憶體資料庫
     */
    @Bean
    @Primary
    @Profile({"dev", "test", "default"})
    public DataSource h2MemoryDataSource() {
        logger.info("正在配置 H2 記憶體資料庫...");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(databaseUrl);
        config.setUsername(databaseUsername);
        config.setPassword(databasePassword);
        config.setDriverClassName(databaseDriverClassName);

        // 連接池配置
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        // H2 專用配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        HikariDataSource dataSource = new HikariDataSource(config);

        logger.info("H2 記憶體資料庫配置完成，連接 URL：{}", config.getJdbcUrl());

        return dataSource;
    }

    /**
     * 生產環境的 H2 檔案資料庫配置
     *
     * @return DataSource H2 檔案資料庫
     */
    @Bean
    @Primary
    @Profile("prod")
    public DataSource h2FileDataSource() {
        logger.info("正在配置 H2 檔案資料庫...");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(databaseUrl);
        config.setUsername(databaseUsername);
        config.setPassword(databasePassword);
        config.setDriverClassName(databaseDriverClassName);

        // 生產環境連接池配置
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);

        // 性能優化配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");

        HikariDataSource dataSource = new HikariDataSource(config);

        logger.info("H2 檔案資料庫配置完成，連接 URL：{}", config.getJdbcUrl());

        return dataSource;
    }

    /**
     * 事務管理器配置
     *
     * @param dataSource 資料來源
     * @return PlatformTransactionManager 事務管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        logger.info("正在配置事務管理器...");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        logger.info("事務管理器配置完成");
        return transactionManager;
    }

}
