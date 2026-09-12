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
public class H5DeviceFact extends AbstractFact {
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
    private Map<String,Object> browser;
    @ValueSet()
    private Map<String,Object> baseInfo;
    @ValueSet()
    private Map<String,Object> document;
    @ValueSet()
    private Map<String,Object> window;
    @ValueSet()
    private Map<String,Object> navigator;
    private Map<String,Object> capabilities;

    public H5DeviceFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);
        capabilities = factMap.get("capabilities") instanceof Map ? (Map<String, Object>) factMap.get("capabilities") : Map.of();
    }

    @Data
    public static class Device{
        private String systemName;
        private String systemVersion;
        private String modelCode;
        private String countryCode;
        private String language;
        private String timeZone;
    }

    @Data
    public static class Screen{
        private Integer width;
        private Integer height;
        private Integer availWidth;
        private Integer availHeight;
        private Integer screenSize;
    }
    @Data
    public static class Cpu{
        private Integer totalCore;
        private String type;
        private String abi;
    }

    @Data
    public static class Uuid{
        private String GUID;
    }

    @Data
    public static class Storage{
        private Double totalDisk;
        private Double totalRam;
    }

    @Data
    public static class Network{
        private String lanIp;
        private String type;
        private String info;
    }
}
