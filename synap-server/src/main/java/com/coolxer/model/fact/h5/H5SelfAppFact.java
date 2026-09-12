package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.ValueSet;
import lombok.Data;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class H5SelfAppFact extends AbstractFact {

    @ValueSet()
    private Base base;

    public H5SelfAppFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Base {
        private String domain;
    }
}
