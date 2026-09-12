package com.coolxer.config;

import com.coolxer.service.ApiConfigLoadService;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.PunishConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 配置文件监听器配置类
 * 统一管理 msg.api.config.path、checker.config.path、punish.config.path 的动态加载
 */
@Configuration
public class ConfigFileMonitor {

    @Value("${msg.api.config.path}")
    private String msgApiConfigPath;

    @Value("${checker.config.path}")
    private String checkerConfigPath;

    @Value("${punish.config.path}")
    private String punishConfigPath;

    private final ApiConfigLoadService apiConfigLoadService;
    private final CheckerConfigService checkerConfigService;
    private final PunishConfigService punishConfigService;

    public ConfigFileMonitor(ApiConfigLoadService apiConfigLoadService,
                            CheckerConfigService checkerConfigService,
                            PunishConfigService punishConfigService) {
        this.apiConfigLoadService = apiConfigLoadService;
        this.checkerConfigService = checkerConfigService;
        this.punishConfigService = punishConfigService;
    }

    @PostConstruct
    public void init() {
        // 初始化 MSG API 配置监听
        apiConfigLoadService.setConfigPath(msgApiConfigPath);
        
        // 初始化 Checker 配置监听（目录）
        checkerConfigService.setConfigDirPath(checkerConfigPath);
        
        // 初始化 Punish 配置监听
        punishConfigService.setConfigPath(punishConfigPath);
        
        // 启动所有监听服务
        apiConfigLoadService.init();
        checkerConfigService.init();
        punishConfigService.init();
    }
}
