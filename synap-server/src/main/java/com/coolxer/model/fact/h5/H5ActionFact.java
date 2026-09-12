package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

@Getter
public class H5ActionFact extends AbstractFact {
    private final Map<String, Object> data;
    public H5ActionFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, (String) factMap.get("buildDate"), header, common);
        data = factMap.get("data") instanceof Map ? (Map<String, Object>) factMap.get("data") : Map.of();
    }
}
