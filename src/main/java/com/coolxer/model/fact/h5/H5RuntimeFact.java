package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.ValueSet;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class H5RuntimeFact extends AbstractFact {

    @ValueSet()
    private Map<String,Object> custom;

    public H5RuntimeFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);
    }
}
