package com.coolxer.service;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Checker配置服务接口
 * 支持按平台动态加载checker配置
 */
public interface CheckerConfigService {

    /**
     * 存储各平台的配置，key为平台名称，value为配置Map
     */
    ConcurrentHashMap<String, Map<String, Object>> platformConfigMap = new ConcurrentHashMap<>();

    /**
     * 获取指定平台的配置
     *
     * @param platform 平台名称 (android, ios, h5, wechat, host)
     * @return 配置Map
     */
    Map<String, Object> getConfigByPlatform(String platform);

    /**
     * 重新加载指定平台的配置
     *
     * @param platform 平台名称
     */
    void reloadPlatformConfig(String platform);

    /**
     * 设置配置目录路径
     *
     * @param configDirPath 配置目录路径
     */
    void setConfigDirPath(String configDirPath);

    /**
     * 初始化并启动文件监听
     */
    void init();

    /**
     * 停止文件监听
     */
    void stop();
}
