package com.coolxer.service;

/**
 * 通用配置文件监听服务接口
 * 支持文件和目录的变更监听，并对外提供最新的配置内容
 */
public interface ConfigWatchService<T> {

    /**
     * 获取最新的配置内容
     *
     * @return 配置对象
     */
    T getConfig();

    /**
     * 设置配置文件或目录路径
     *
     * @param configPath 配置文件或目录路径
     */
    void setConfigPath(String configPath);

    /**
     * 初始化配置并启动文件监听
     */
    void init();

    /**
     * 停止文件监听
     */
    void stop();

    /**
     * 重新加载配置
     */
    void reload();
}
