package com.coolxer.model.fact.android;

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
public class AndroidDeviceFact extends AbstractFact {

    @ValueSet()
    private Device device;
    @ValueSet()
    private Screen screen;
    @ValueSet()
    private Cpu cpu;
    @ValueSet()
    private Map<String,Object> battery;
    @ValueSet()
    private Uuid uuid;
    @ValueSet()
    private Network network;
    @ValueSet()
    private Storage storage;
    @ValueSet()
    private Map<String,Object> opengl;
    @ValueSet()
    private Map<String,Object> prop;
    @ValueSet()
    private Map<String,Object> build;

    public AndroidDeviceFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"),header,common);
        build(factMap);
    }

    @Data
    public static class Screen{
        private Integer width;
        private Integer height;
        private Double brightness;
        private Double density;
        private Integer rotation;
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
    public static class Device{
        private String type;
        private String name;
        private String systemName;
        private String systemVersion;
        private String buildId;
        private String modelCode;
        private String countryCode;
        private String language;
        private String timeZone;
        private String currency;
        private Date startElapsedTime;
        private Boolean wireConnected;
    }
    @Data
    public static class Uuid{
        private String macWlan0;
        private String macWlan1;
        private String macP2p0;
        private String imei;
        private String imsi;
        private String iccid;
        private String serial;
        private String fileUidData;
        private String fileUidSystem;
        private String fileUidCache;
        private String fileUidVendor;
        private String androidId;
        private String widevineId;
        private String oaid;
        private String bootId;
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
