package com.coolxer.model.fact.wechat;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

@Getter
public class WechatErrorFact extends AbstractFact {
    private final String category;
    private final String message;
    private final String source;
    private final String fingerprint;
    private final int count;

    public WechatErrorFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, (String) factMap.get("buildDate"), header, common);
        category = String.valueOf(factMap.getOrDefault("category", "unknown"));
        message = String.valueOf(factMap.getOrDefault("message", ""));
        source = String.valueOf(factMap.getOrDefault("source", ""));
        fingerprint = String.valueOf(factMap.getOrDefault("fingerprint", ""));
        Object value = factMap.get("count");
        count = value instanceof Number ? ((Number) value).intValue() : 1;
    }
}
