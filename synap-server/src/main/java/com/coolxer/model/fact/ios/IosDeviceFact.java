package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.ValueSet;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.Map;

/**

 */

@Slf4j
@Getter
public class IosDeviceFact extends AbstractFact {

    @ValueSet()
    private Device device;
    @ValueSet()
    private Screen screen;
    @ValueSet()
    private Cpu cpu;
    @ValueSet()
    private Battery battery;
    @ValueSet()
    private Uuid uuid;
    @ValueSet()
    private Network network;
    @ValueSet()
    private Storage storage;
    @ValueSet()
    private Map<String,Object> sysctl;

    public IosDeviceFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"),header,common);
        build(factMap);

    }

    @Data
    public static class Screen{
        private Integer width;
        private Integer height;
        private Double brightness;
    }
    @Data
    public static class Cpu{
        private Integer totalCore;
        private Integer usedCore;
        private String type;
        private String abi;
    }
    @Data
    public static class Storage{
        private Double totalDisk;
        private Double usedDisk;
        private Double totalRam;
        private Double usedRam;
    }

    @Data
    public static class Battery{
        private Double capacity;
        private Double level;
        private Boolean charging;
        private Boolean charged;
    }
    @Data
    public static class Device{
        private String type;
        private String name;
        private Boolean simulator;
        private String systemName;
        private String systemVersion;
        private String buildVersion;
        private String kernelVersion;
        private String modelCode;
        private String countryCode;
        private String language;
        private String timeZone;
        private String currency;
        private Date activationTime;
        private Date startupTime;
        private Date fileCreationTime;
        private Date fileModificationTime;
        private Boolean wireConnected;
    }
    @Data
    public static class Uuid{
        private String cfuuid;
        private String idfa;
        private String idfv;
        private String guid;
    }
    @Data
    public static class Network{
        private Integer simCount;
        private String cellType;
        private String operators;
        private String country;
        private String mcc;
        private String iso;
        private String mnc;
        private String allowsVoip;
        private String lanIp;
        private String mask;
        private String broadcast;
        private String routing;
        private String signal;
        private String type;
    }


}
