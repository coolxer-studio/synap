package com.coolxer.model.fact.android;

import com.coolxer.model.fact.*;
import com.coolxer.utils.JacksonUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public class AndroidSelfAppFact extends AbstractFact {
    private AndroidAppFact.App base;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> procMaps;

    public AndroidSelfAppFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

        Map baseMap = FactBuilder.mergeValueAndKey(AndroidAppFact.App.APP_KEYS,(List<Object>) factMap.get("base"));
        try {
            this.base = JacksonUtils.toObject(JacksonUtils.toJson(baseMap),AndroidAppFact.App.class);
        } catch (JsonProcessingException e) {
            log.error("解析AndroidSelfAppFact base对象失败", e);
        }

    }
}
