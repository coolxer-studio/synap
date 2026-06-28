package com.coolxer.service.impl;

import com.coolxer.service.CheckerConfigService;
import com.coolxer.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checker配置服务实现
 * 支持目录监听，动态加载不同平台的checker配置
 */
@Slf4j
@Service
public class CheckerConfigServiceImpl implements CheckerConfigService {

    private String configDirPath;

    @Override
    public void init() {
        if (configDirPath != null && !configDirPath.isEmpty()) {
            // 初始加载所有平台配置
            loadAllPlatformConfigs();
            // 启动文件监听（需要单独实现目录监听）
            startFileWatcher();
        }
    }

    @Override
    public void stop() {
        log.info("Checker配置监听已停止: {}", configDirPath);
    }

    @Override
    public Map<String, Object> getConfigByPlatform(String platform) {
        return platformConfigMap.get(platform);
    }

    @Override
    public void reloadPlatformConfig(String platform) {
        if (configDirPath == null || configDirPath.isEmpty()) {
            log.warn("配置目录路径为空");
            return;
        }
        
        String configFilePath = configDirPath + platform + ".json";
        try {
            String content = new String(Files.readAllBytes(Paths.get(configFilePath)));
            Map<String, Object> config = JacksonUtils.toMap(content);
            platformConfigMap.put(platform, config);
            log.info("重新加载 {} 平台配置成功", platform);
        } catch (IOException e) {
            log.error("加载 {} 平台配置文件失败: {}", platform, configFilePath, e);
        }
    }

    @Override
    public void setConfigDirPath(String configDirPath) {
        this.configDirPath = configDirPath;
        if (!configDirPath.endsWith("/")) {
            this.configDirPath += "/";
        }
        // 如果服务已初始化但尚未启动，则现在启动
        if (platformConfigMap.isEmpty() && configDirPath != null && !configDirPath.isEmpty()) {
            init();
        }
    }

    /**
     * 加载所有平台的配置
     */
    private void loadAllPlatformConfigs() {
        String[] platforms = {"android", "ios", "h5", "wechat", "host"};
        for (String platform : platforms) {
            reloadPlatformConfig(platform);
        }
        log.info("所有平台配置加载完成，共 {} 个平台", platformConfigMap.size());
    }

    /**
     * 启动文件监听器
     */
    private void startFileWatcher() {
        if (configDirPath == null || configDirPath.isEmpty()) {
            log.warn("配置目录路径为空，无法启动文件监听");
            return;
        }

        try {
            java.nio.file.Path path = Paths.get(configDirPath);
            if (!Files.isDirectory(path)) {
                log.warn("配置路径不是目录: {}", configDirPath);
                return;
            }

            java.nio.file.WatchService watchService = java.nio.file.FileSystems.getDefault().newWatchService();
            path.register(watchService, 
                java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY, 
                java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);

            Thread watchThread = new Thread(() -> {
                while (true) {
                    try {
                        java.nio.file.WatchKey key = watchService.take();
                        
                        for (java.nio.file.WatchEvent<?> event : key.pollEvents()) {
                            java.nio.file.WatchEvent.Kind<?> kind = event.kind();
                            
                            if (kind == java.nio.file.StandardWatchEventKinds.OVERFLOW) {
                                continue;
                            }
                            
                            @SuppressWarnings("unchecked")
                            java.nio.file.WatchEvent<java.nio.file.Path> ev = (java.nio.file.WatchEvent<java.nio.file.Path>) event;
                            java.nio.file.Path fileName = ev.context();
                            
                            // 检查是否是json文件
                            if (fileName.toString().endsWith(".json")) {
                                log.info("检测到checker配置文件变更: {}", fileName);
                                // 提取平台名称（去掉.json后缀）
                                String platform = fileName.toString().replace(".json", "");
                                reloadPlatformConfig(platform);
                            }
                        }
                        
                        boolean valid = key.reset();
                        if (!valid) {
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                try {
                    watchService.close();
                } catch (IOException e) {
                    log.error("关闭 WatchService 失败", e);
                }
            }, "checker-config-watcher");
            
            watchThread.setDaemon(true);
            watchThread.start();
            
            log.info("Checker配置文件监听已启动: {}", configDirPath);
        } catch (IOException e) {
            log.error("启动文件监听失败: {}", configDirPath, e);
        }
    }
}
