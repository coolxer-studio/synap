package com.coolxer.model.fact.android;


import com.coolxer.model.fact.*;
import lombok.Data;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Getter
public class AndroidRuntimeFact extends AbstractFact {

    @ValueSet(valueType= ValueType.VALUE)
    private ArrayList<String> service;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> usageStats;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> mount;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> display;
    @ValueSet(valueType=ValueType.KEY)
    private ArrayList<String> hasClass;
    @ValueSet(valueType=ValueType.KEY)
    private ArrayList<String> hasInstance;
    @ValueSet(valueType=ValueType.KEY)
    private ArrayList<String> hasService;
    @ValueSet(valueType=ValueType.VALUE)
    private ArrayList<String> process;
    @ValueSet()
    private Map<String,Object> cmd;
    @ValueSet()
    private Custom custom;

    public AndroidRuntimeFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Custom{
        private Boolean usbDebug;
        private Boolean tcpDebug;
        private Boolean mockLocation;
        private Boolean debugConnect;
        private String ptrace;
        private Boolean frida;
    }
}
