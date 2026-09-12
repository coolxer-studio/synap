package com.coolxer.model.fact.ios;

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
public class IosSelfAppFact extends AbstractFact {
    @ValueSet()
    private Map<String,Object> environment;
    private IosAppFact.App base;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> permission;

    public IosSelfAppFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);

        Map baseMap = FactBuilder.mergeValueAndKey(IosAppFact.App.APP_KEYS,(List<Object>) factMap.get("base"));
        try {
            this.base = JacksonUtils.toObject(JacksonUtils.toJson(baseMap),IosAppFact.App.class);
        } catch (JsonProcessingException e) {
            log.error("解析iOS SelfApp base对象失败", e);
        }

    }
}
