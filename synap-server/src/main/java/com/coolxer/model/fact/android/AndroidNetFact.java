package com.coolxer.model.fact.android;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class AndroidNetFact extends AbstractFact {
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
    @ValueSet(valueType=ValueType.KEY)
    private ArrayList<String> sslPing;
    @ValueSet()
    private Map<String,Object> uds;
    @ValueSet()
    private Map<String,Object> custom;


    public AndroidNetFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }

}
