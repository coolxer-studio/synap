package com.coolxer.service;

import com.coolxer.model.cross.APIConfig;

import java.util.List;

/**
 * MSG API 配置加载服务接口
 */
public interface ApiConfigLoadService {

    /**
     * 获取所有配置
     *
     * @return 所有API配置列表
     */
    List<APIConfig> getAllConfig();

    /**
     * 根据 ID 获取配置
     *
     * @param id 配置ID
     * @return API配置对象
     */
    APIConfig getConfigById(String id);

    /**
     * 设置配置文件路径（由 Spring 注入）
     *
     * @param configPath 配置文件路径
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
}
