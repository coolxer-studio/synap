package com.coolxer.service.impl;

import com.coolxer.model.cross.APIConfig;
import com.coolxer.service.ApiConfigLoadService;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MSG API 配置加载服务实现
 * 继承自 AbstractConfigWatchService，实现配置文件动态监听和加载
 */
@Slf4j
@Service
public class ApiConfigLoadServiceImpl extends AbstractConfigWatchService<Map<String, APIConfig>> implements ApiConfigLoadService {

    /**
     * 加载 MSG API 配置文件
     *
     * @param path 配置文件路径
     * @return 以 id 为 key 的配置 Map
     */
    @Override
    protected Map<String, APIConfig> loadConfig(String path) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            List<APIConfig> configList = JacksonUtils.toList(content, new TypeReference<List<APIConfig>>() {});
            // 将列表转换为以 id 为 key 的 Map
            return configList.stream()
                    .filter(config -> config.getId() != null)
                    .collect(Collectors.toMap(APIConfig::getId, config -> config, (existing, replacement) -> existing));
        } catch (IOException e) {
            log.error("加载 MSG API 配置文件失败: {}", path, e);
            return new HashMap<>();
        }
    }

    /**
     * 获取配置文件名
     *
     * @return 配置文件名
     */
    @Override
    protected String getConfigFileName() {
        if (configPath != null && !configPath.isEmpty()) {
            return Paths.get(configPath).getFileName().toString();
        }
        return "msg_api_config.json";
    }

    /**
     * 获取所有配置
     */
    @Override
    public List<APIConfig> getAllConfig() {
        Map<String, APIConfig> configMap = getConfig();
        return configMap != null ? new java.util.ArrayList<>(configMap.values()) : new java.util.ArrayList<>();
    }

    /**
     * 根据 ID 获取配置
     */
    @Override
    public APIConfig getConfigById(String id) {
        Map<String, APIConfig> configMap = getConfig();
        return configMap != null ? configMap.get(id) : null;
    }
}
