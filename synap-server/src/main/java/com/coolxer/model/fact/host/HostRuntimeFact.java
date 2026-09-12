package com.coolxer.model.fact.host;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class HostRuntimeFact extends AbstractFact {

    @ValueSet(valueType= ValueType.VALUE)
    private ArrayList<String> process;
    @ValueSet()
    private Map<String,Object> env;
    @ValueSet()
    private Map<String,Object> cmd;

    public HostRuntimeFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);
    }
}
