package com.coolxer.model.fact.android;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.utils.JacksonUtils;
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
public class AndroidPrivacyFact extends AbstractFact {
    private Privacy base;

    public AndroidPrivacyFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        Map privacyMap = FactBuilder.mergeValueAndKey(Privacy.PRIVACY_KEYS,(List<Object>) factMap.get("base"));
        try {
            base = JacksonUtils.toObject(JacksonUtils.toJson(privacyMap),Privacy.class);
        } catch (JsonProcessingException e) {
            log.error("解析Privacy对象失败", e);
        }
    }

    @Data
    public static class Privacy{
        public static List<String> PRIVACY_KEYS;

        static {
            try {
                PRIVACY_KEYS = JacksonUtils.toStringList("[\"caller\",\"method\"]");
            } catch (JsonProcessingException e) {
                log.error("解析PRIVACY_KEYS配置失败", e);
            }
        }

        private String caller;
        private String method;
    }
}
