package com.coolxer.model.fact.android;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class AndroidFileFact extends AbstractFact {
    @ValueSet(valueType= ValueType.KEY)
    private ArrayList<String> exist;
    @ValueSet()
    private Map<String,Object> access;
    @ValueSet()
    private Map<String,Object> context;
    @ValueSet()
    private Map<String,Object> fileList;
    @ValueSet()
    private Map<String,Object> md5;

    public AndroidFileFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);

    }
}
