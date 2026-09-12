package com.coolxer.model.fact.android;

import com.coolxer.model.fact.*;
import com.coolxer.utils.JacksonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**

 */
@Slf4j
@Getter
public class AndroidAppFact extends AbstractFact {
    private ArrayList<App> installed = new ArrayList<>();
    @ValueSet()
    private Map<String,Object> entry;

    public AndroidAppFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

        List<Object> installedList = (List<Object>) factMap.get("installed");
        for (int i = 0; i < installedList.size(); i++) {
            Map appMap = FactBuilder.mergeValueAndKey(App.APP_KEYS,(List<Object>) installedList.get(i));
            App app = null;
            try {
                app = JacksonUtils.toObject(JacksonUtils.toJson(appMap), App.class);
            } catch (JsonProcessingException e) {
                log.error("解析App对象失败", e);
            }
            if(app!=null){
                installed.add(app);
            }
        }
    }

    @Data
    public static class App{
        public static List<String> APP_KEYS;

        static {
            try {
                APP_KEYS = JacksonUtils.toStringList("[\"appName\",\"packageName\",\"versionName\",\"versionCode\",\"md5\",\"certMd5\",\"certIssuer\",\"installTime\",\"updateTime\",\"applicationInfoFlags\",\"from\"]");
            } catch (JsonProcessingException e) {
                log.error("解析APP_KEYS配置失败", e);
            }
        }

        private String appName;
        private String packageName;
        private String versionName;
        private String versionCode;
        private String md5;
        private String certMd5;
        private String certIssuer;
        private Date installTime;
        private Date updateTime;
        private int applicationInfoFlags;
        private String from;
    }
}
