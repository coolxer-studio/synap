package com.coolxer.model.fact.wechat;

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
public class WechatDeviceFact extends AbstractFact {
    @ValueSet()
    private Device device;
    @ValueSet()
    private Screen screen;
    @ValueSet()
    private SystemSetting systemSetting;
    @ValueSet()
    private AppBaseInfo appBaseInfo;
    @ValueSet()
    private AppAuthorizeSetting appAuthorizeSetting;
    @ValueSet()
    private Battery battery;
    @ValueSet()
    private Uuid uuid;

    public WechatDeviceFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        build(factMap);
    }

    @Data
    public static class Device{
        private String abi;
        private String deviceAbi;
        private Integer benchmarkLevel;
        private String brand;
        private String model;
        private String system;
        private String platform;
        private String cpuType;
        private String memorySize;
    }

    @Data
    public static class Screen{
        private Integer pixelRatio;
        private Integer screenHeight;
        private Integer screenWidth;
        private Integer windowHeight;
        private Integer windowWidth;
        private Integer statusBarHeight;
        private Integer screenTop;
    }

    @Data
    public static class SystemSetting{
        private Boolean bluetoothEnabled;
        private String deviceOrientation;
        private Boolean locationEnabled;
        private Boolean wifiEnabled;
    }

    @Data
    public static class AppBaseInfo{
        private String SDKVersion;
        private Boolean enableDebug;
        private String language;
        private String version;
        private Integer fontSizeScaleFactor;
        private Integer fontSizeSetting;
    }

    @Data
    public static class AppAuthorizeSetting{
        private String albumAuthorized;
        private String bluetoothAuthorized;
        private String cameraAuthorized;
        private String locationAuthorized;
        private String locationReducedAccuracy;
        private String microphoneAuthorized;
        private String notificationAlertAuthorized;
        private String notificationAuthorized;
        private String notificationBadgeAuthorized;
        private String notificationSoundAuthorized;
        private String phoneCalendarAuthorized;
    }

    @Data
    public static class Battery{
        private String level;
        private Boolean isCharging;
    }

    @Data
    public static class Uuid{
        private String GUID;
    }

}
