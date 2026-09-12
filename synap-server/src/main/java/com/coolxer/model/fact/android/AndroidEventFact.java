package com.coolxer.model.fact.android;


import com.coolxer.model.fact.*;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class AndroidEventFact extends AbstractFact {
    @ValueSet(valueType= ValueType.VALUE)
    private ArrayList<Integer> memModify;


    public AndroidEventFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);

    }
}
