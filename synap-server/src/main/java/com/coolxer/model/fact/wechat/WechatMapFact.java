package com.coolxer.model.fact.wechat;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
public abstract class WechatMapFact extends AbstractFact {
    private final Map<String, Object> data;

    @SuppressWarnings("unchecked")
    protected WechatMapFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, (String) factMap.get("buildDate"), header, common);
        Object value = factMap.get("data");
        data = value instanceof Map<?, ?> ? (Map<String, Object>) value : Collections.emptyMap();
    }
}
