package com.coolxer.model.fact.ios;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class IosInjectFact extends AbstractFact {

    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> classes;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> swizzling;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> inlineHook;
    @ValueSet(valueType = ValueType.KEY)
    private ArrayList<String> fishhook;

    public IosInjectFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);
    }
}
