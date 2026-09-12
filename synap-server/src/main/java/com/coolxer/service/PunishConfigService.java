package com.coolxer.service;

import com.coolxer.model.punish.Punish;

import java.util.List;

/**
 * Punish配置服务接口
 * 支持动态加载惩罚规则配置
 */
public interface PunishConfigService {

    /**
     * 获取所有惩罚规则
     *
     * @return 惩罚规则列表
     */
    List<Punish> getAllPunishRules();

    /**
     * 设置配置文件路径
     *
     * @param configPath 配置文件路径
     */
    void setConfigPath(String configPath);

    /**
     * 初始化并启动文件监听
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
