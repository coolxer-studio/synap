package com.coolxer.service.impl;

import com.coolxer.model.punish.Punish;
import com.coolxer.service.PunishConfigService;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Punish配置服务实现
 * 继承自 AbstractConfigWatchService，实现惩罚规则配置的动态监听和加载
 */
@Slf4j
@Service
public class PunishConfigServiceImpl extends AbstractConfigWatchService<List<Punish>> implements PunishConfigService {

    /**
     * 加载 Punish 配置文件
     *
     * @param path 配置文件路径
     * @return 惩罚规则列表
     */
    @Override
    protected List<Punish> loadConfig(String path) {
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            return JacksonUtils.toList(content, new TypeReference<List<Punish>>() {});
        } catch (IOException e) {
            log.error("加载 Punish 配置文件失败: {}", path, e);
            return new ArrayList<>();
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
        return "punish_config.json";
    }

    /**
     * 获取所有惩罚规则
     */
    @Override
    public List<Punish> getAllPunishRules() {
        List<Punish> rules = getConfig();
        return rules != null ? rules : new ArrayList<>();
    }
}
