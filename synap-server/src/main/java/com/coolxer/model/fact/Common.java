package com.coolxer.model.fact;

import lombok.Data;

/**

 */
@Data
public class Common {
    private String userId;
    private String guid;
    private long startId;
    private String sdkVersion;
    private int appId;
    private String appName;
    private String appPackage;
    private String appVersion;
    private String platform;
    private String manufacturer;
    private String model;
    private String system;
    private String systemVersion;
    private String netType;
    private String lanIp;
    private String wanIp;
    private double latitude;
    private double longitude;
    private String country;
    private String province;
    private String city;
    private String county;
    private String thoroughfare;
    private String clientTime;
}
