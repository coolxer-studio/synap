package com.coolxer.model.fact.host;

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
public class HostDeviceFact extends AbstractFact {

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

    public HostDeviceFact(String type, Header header, Common common, Map<String,Object> factMap) {
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
    }
    @Data
    public static class Uuid{
        private String macEth0;
        private String serial;
        private String bootId;
        private String guid;

    }

    @Data
    public static class Network{
        private String lanIp;
        private String mask;
        private String broadcast;
        private String routing;
        private String signal;
        private String type;
    }

}
