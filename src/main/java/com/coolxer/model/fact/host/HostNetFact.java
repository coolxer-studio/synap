package com.coolxer.model.fact.host;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class HostNetFact extends AbstractFact {
    @ValueSet(valueType= ValueType.KEY)
    private ArrayList<String> socket;
    @ValueSet(valueType=ValueType.KEY)
    private ArrayList<String> port;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> httpProxy;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> netInterface;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> hosts;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> certificate;
    @ValueSet()
    private Map<String,Object> uds;


    public HostNetFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }

}
