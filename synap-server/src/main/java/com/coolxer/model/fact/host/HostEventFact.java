package com.coolxer.model.fact.host;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class HostEventFact extends AbstractFact {
    @ValueSet(valueType= ValueType.VALUE)
    private ArrayList<Object> sshd;


    public HostEventFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }
}
