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

import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public class IosViewFact extends AbstractFact {
    private View view;

    public IosViewFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);

        Map baseMap = FactBuilder.mergeValueAndKey(View.VIEW_KEYS,(List<Object>) factMap.get("base"));
        try {
            view = JacksonUtils.toObject(JacksonUtils.toJson(baseMap),View.class);
        } catch (JsonProcessingException e) {
            log.error("解析iOS View对象失败", e);
        }
    }

    @Data
    public static class View {
        public static List<String> VIEW_KEYS;

        static {
            try {
                VIEW_KEYS = JacksonUtils.toStringList("[\"className\",\"title\"]");
            } catch (JsonProcessingException e) {
                log.error("解析iOS VIEW_KEYS配置失败", e);
            }
        }

        private String className;
        private String title;
    }
}
