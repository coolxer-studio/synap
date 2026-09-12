package com.coolxer.service.impl;

import com.coolxer.service.ConfigWatchService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.function.Function;

/**
 * 通用配置文件监听服务实现
 * 支持单文件和目录的变更监听
 *
 * @param <T> 配置类型
 */
@Slf4j
public abstract class AbstractConfigWatchService<T> implements ConfigWatchService<T> {

    protected String configPath;
    protected volatile T config;
    
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean running = false;

    /**
     * 加载配置的抽象方法，由子类实现具体的加载逻辑
     *
     * @param path 配置文件路径
     * @return 配置对象
     */
    protected abstract T loadConfig(String path);

    /**
     * 获取配置文件的名称（用于文件监听时匹配）
     *
     * @return 配置文件名
     */
    protected abstract String getConfigFileName();

    @Override
    public void init() {
        if (configPath != null && !configPath.isEmpty()) {
            reload();
            startFileWatcher();
        }
    }

    @Override
    public void reload() {
        if (configPath != null && !configPath.isEmpty()) {
            try {
                T newConfig = loadConfig(configPath);
                if (newConfig != null) {
                    this.config = newConfig;
                    log.info("配置加载成功: {}", configPath);
                } else {
                    log.warn("配置加载返回null: {}", configPath);
                }
            } catch (Exception e) {
                log.error("加载配置文件失败: {}", configPath, e);
            }
        }
    }

    @Override
    public T getConfig() {
        return config;
    }

    @Override
    public void setConfigPath(String configPath) {
        this.configPath = configPath;
        // 如果服务已初始化但尚未启动监听，则现在启动
        if (!running && configPath != null && !configPath.isEmpty()) {
            reload();
            startFileWatcher();
        }
    }

    @Override
    public void stop() {
        running = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("关闭 WatchService 失败", e);
            }
        }
        log.info("配置文件监听已停止: {}", configPath);
    }

    /**
     * 启动文件监听器
     */
    private void startFileWatcher() {
        if (configPath == null || configPath.isEmpty()) {
            log.warn("配置文件路径为空，无法启动文件监听");
            return;
        }

        try {
            Path path = Paths.get(configPath);
            Path directory;
            
            // 判断是文件还是目录
            if (Files.isDirectory(path)) {
                directory = path;
            } else {
                directory = path.getParent();
                if (directory == null) {
                    log.warn("无法获取配置文件目录: {}", configPath);
                    return;
                }
            }

            watchService = FileSystems.getDefault().newWatchService();
            directory.register(watchService, 
                StandardWatchEventKinds.ENTRY_MODIFY, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE);

            running = true;
            String threadName = "config-watcher-" + getConfigFileName();
            watchThread = new Thread(this::watchLoop, threadName);
            watchThread.setDaemon(true);
            watchThread.start();
            
            log.info("配置文件监听已启动: {}, 监听目录: {}", configPath, directory);
        } catch (IOException e) {
            log.error("启动文件监听失败: {}", configPath, e);
        }
    }

    /**
     * 文件监听循环
     */
    private void watchLoop() {
        while (running) {
            try {
                WatchKey key = watchService.take();
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    
                    // 检查是否是目标配置文件或目录中的任意文件变化
                    if (shouldReload(fileName.toString())) {
                        log.info("检测到配置文件变更: {}", fileName);
                        reload();
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
    }

    /**
     * 判断是否应该重新加载配置
     * 如果是单文件模式，只监听特定文件；如果是目录模式，监听所有json文件
     *
     * @param fileName 变化的文件名
     * @return 是否应该重新加载
     */
    protected boolean shouldReload(String fileName) {
        Path path = Paths.get(configPath);
        if (Files.isDirectory(path)) {
            // 目录模式：监听所有json文件
            return fileName.endsWith(".json");
        } else {
            // 单文件模式：只监听特定文件
            return fileName.equals(getConfigFileName());
        }
    }
}
