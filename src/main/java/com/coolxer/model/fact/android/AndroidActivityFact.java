package com.coolxer.model.fact.android;

import com.coolxer.utils.JacksonUtils;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public class AndroidActivityFact extends AbstractFact {
    private Activity base;

    public AndroidActivityFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        Map baseMap = FactBuilder.mergeValueAndKey(Activity.ACTIVITY_KEYS,(List<Object>)factMap.get("base"));
        try {
            base = JacksonUtils.toObject(JacksonUtils.toJson(baseMap),Activity.class);
        } catch (JsonProcessingException e) {
            log.error("解析Activity对象失败", e);
        }
    }

    @Data
    public static class Activity{
        public static List<String> ACTIVITY_KEYS;

        static {
            try {
                ACTIVITY_KEYS = JacksonUtils.toStringList("[\"status\",\"time\",\"className\",\"title\",\"intent\"]");
            } catch (JsonProcessingException e) {
                log.error("解析ACTIVITY_KEYS配置失败", e);
            }
        }

        private String status;
        private String time;
        private String className;
        private String title;
        private String intent;
    }
}
