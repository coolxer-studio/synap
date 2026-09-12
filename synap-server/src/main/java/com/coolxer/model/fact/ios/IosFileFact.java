package com.coolxer.model.fact.ios;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class IosFileFact extends AbstractFact {
    @ValueSet(valueType = ValueType.KEY)
    private ArrayList<String> exist;
    @ValueSet()
    private Map<String,Object> access;
    @ValueSet()
    private Map<String,Object> context;
    @ValueSet()
    private Map<String,Object> linkFiles;
    @ValueSet()
    private Map<String,Object> fileList;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> infoPlist;

    public IosFileFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }
}
