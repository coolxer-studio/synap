package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.ValueSet;
import lombok.Data;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class IosLocationFact extends AbstractFact {
    @ValueSet()
    private Base base;

    public IosLocationFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Base {

        private long timestamp;
        private double speed;
        private double latitude;
        private double longitude;
        private String country;
        private String province;
        private String city;
        private String county;
        private String thoroughfare;
    }
}
