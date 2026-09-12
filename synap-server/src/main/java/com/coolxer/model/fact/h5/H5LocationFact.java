package com.coolxer.model.fact.h5;

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
public class H5LocationFact extends AbstractFact {

    @ValueSet()
    private Base base;

    public H5LocationFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Base {

        private long timestamp;
        private double speed;
        private double latitude;
        private double longitude;
        private double accuracy;
        private Double altitude;
        private Double heading;
        private String permissionState;
        private String status;
        private String country;
        private String province;
        private String city;
        private String county;
        private String thoroughfare;
    }
}
