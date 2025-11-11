package com.bot.fsap.flowable.process.config;

import org.flowable.spring.boot.FlowableProperties;
import org.flowable.spring.boot.process.FlowableProcessProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Flowable 流程引擎主配置類別
 *
 * <p>此配置類別負責整合所有 Flowable 相關配置，包含：</p>
 * <ul>
 *   <li>流程引擎配置</li>
 *   <li>資料來源配置</li>
 *   <li>自動部署配置</li>
 * </ul>
 *
 * @author Blue Flowable Team
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties({
    FlowableProperties.class,
    FlowableProcessProperties.class
})
@Import({
    ProcessEngineConfiguration.class,
    DataSourceConfiguration.class,
    ProcessDeploymentConfiguration.class
})
public class FlowableConfiguration {

    /**
     * 配置類別標識
     */
    public static final String CONFIGURATION_NAME = "flowable-configuration";

}
