package com.coolxer.model.fact.ios;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class IosNetFact extends AbstractFact {
    @ValueSet(valueType = ValueType.KEY)
    private ArrayList<String> socket;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> httpProxy;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> netInterface;
    @ValueSet(valueType = ValueType.VALUE)
    private ArrayList<String> hosts;
    @ValueSet(valueType = ValueType.KEY)
    private ArrayList<String> sslPing;
    @ValueSet()
    private Map<String,Object> response;
    @ValueSet()
    private Map<String,Object> custom;

    public IosNetFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }

}
