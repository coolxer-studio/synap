package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.utils.JacksonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public class IosAppFact extends AbstractFact {
    private ArrayList<App> installed = new ArrayList<>();

    public IosAppFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        List<Object> installedAppList = (List<Object>) factMap.get("installed");
        for (int i = 0; i < installedAppList.size(); i++) {
            Map installAppMap = FactBuilder.mergeValueAndKey(App.APP_KEYS,(List<Object>) installedAppList.get(i));
            App installApp = null;
            try {
                installApp = JacksonUtils.toObject(JacksonUtils.toJson(installAppMap),App.class);
            } catch (JsonProcessingException e) {
                log.error("解析iOS App对象失败", e);
            }
            if(installAppMap != null){
                installed.add(installApp);
            }
        }
    }

    @Data
    public static class App{
        public static List<String> APP_KEYS;

        static {
            try {
                APP_KEYS = JacksonUtils.toStringList("[\"appName\",\"packageName\",\"versionName\",\"versionCode\",\"md5\",\"certMd5\",\"certIssuer\",\"installTime\",\"updateTime\"]");
            } catch (JsonProcessingException e) {
                log.error("解析iOS APP_KEYS配置失败", e);
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
    }
}
