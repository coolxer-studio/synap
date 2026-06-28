package com.coolxer.config;

import com.coolxer.service.CheckerConfigService;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;


/**

 */
@Slf4j
public class CheckerConfig {

    private volatile static CheckerConfig instance;

    public static CheckerConfig getInstance() {

        if (instance == null) {
            synchronized (CheckerConfig.class) {
                if (instance == null) {
                    instance = new CheckerConfig();
                }
            }
        }
        return instance;
    }

    @Getter
    private Map<String,Object> checkerConfig;

    @Getter
    private List<String> commonConfig;

    private CheckerConfig(){

        String commonListString = """
                [
                    "userId",
                    "guid",
                    "startId",
                    "sdkVersion",
                    "appId",
                    "appName",
                    "appPackage",
                    "appVersion",
                    "platform",
                    "manufacturer",
                    "model",
                    "system",
                    "systemVersion",
                    "netType",
                    "lanIp",
                    "wanIp",
                    "latitude",
                    "longitude",
                    "country",
                    "province",
                    "city",
                    "county",
                    "thoroughfare",
                    "clientTime"
                ]
                """;
        try {
            commonConfig = JacksonUtils.toStringList(commonListString);
        } catch (JsonProcessingException e) {
            log.error("解析commonConfig配置失败", e);
        }

        String checkerConfigString = "{}";
        try {
            this.checkerConfig = JacksonUtils.toMap(checkerConfigString);
        } catch (JsonProcessingException e) {
            log.error("解析checkerConfig配置失败", e);
        }
    }

    public Map<String,Object> getConfigWithFactType(String platform,String type){
        this.checkerConfig = CheckerConfigService.platformConfigMap.get(platform.toLowerCase(Locale.ROOT));
        return (Map<String,Object>)this.checkerConfig.get(StringUtils.uncapitalize(type).replace("Data","Config"));
    }
    
}
