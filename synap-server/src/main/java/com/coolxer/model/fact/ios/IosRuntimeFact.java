package com.coolxer.model.fact.ios;


import com.coolxer.model.fact.*;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class IosRuntimeFact extends AbstractFact {

    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> images;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> stackDylib;
    @ValueSet()
    private Map<String,Object> resource;
    @ValueSet()
    private Map<String,Object> bundle;
    @ValueSet()
    private Map<String,Object> process;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> thread;
    @ValueSet()
    private Custom custom;

    public IosRuntimeFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Custom{
        private Boolean isCaptured;
        private Boolean isiOSAppOnMac;
        private Boolean ptrace;
        private Boolean statHook;
    }
}
