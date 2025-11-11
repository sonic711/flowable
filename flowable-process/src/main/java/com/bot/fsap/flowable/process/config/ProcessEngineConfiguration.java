package com.bot.fsap.flowable.process.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Flowable 流程引擎自定義配置類別
 *
 * <p>此配置類別負責設定額外的 Flowable 相關配置：</p>
 * <ul>
 *   <li>流程引擎配置客製化</li>
 *   <li>監控和觀察配置</li>
 *   <li>自定義攔截器</li>
 * </ul>
 *
 * <p>注意：使用 Flowable Spring Boot Starter 時，基本的流程引擎配置由自動配置類別處理</p>
 *
 * @author Blue Flowable Team
 * @since 1.0.0
 */
@Configuration
public class ProcessEngineConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ProcessEngineConfiguration.class);

    @Autowired
    private Environment environment;

    /**
     * Flowable 引擎配置客製化器
     * 用於客製化 Flowable 自動配置的設定
     */
    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableEngineConfigurationConfigurer() {
        return processEngineConfiguration -> {
            logger.info("正在客製化 Flowable 流程引擎配置...");

            // 設定資料庫 schema 更新策略
            String schemaUpdate = environment.getProperty("flowable.process.database-schema-update", "create-drop");
            processEngineConfiguration.setDatabaseSchemaUpdate(schemaUpdate);

            // 設定異步執行器
            boolean asyncExecutorActivate = environment.getProperty("flowable.process.async-executor-activate", Boolean.class, true);
            processEngineConfiguration.setAsyncExecutorActivate(asyncExecutorActivate);

            // 設定歷史記錄級別
            String historyLevel = environment.getProperty("flowable.process.history-level", "full");
            processEngineConfiguration.setHistory(historyLevel);

            logger.info("Flowable 流程引擎配置客製化完成");
        };
    }

}
