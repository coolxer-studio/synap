package com.coolxer.model.fact.android;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class AndroidInjectFact extends AbstractFact {
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> libHook;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> javaHook;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> xposedHook;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> proxyHook;

    public AndroidInjectFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }
}
