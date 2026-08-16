package com.coolxer.synap.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties("synap.agent")
public class SynapAgentProperties {
    private boolean enabled = true;
    private String serverUrl = "";
    private int appId;
    private String appName = "";
    private String appVersion = "unknown";
    private String appKeyId = "";
    private String serverKeyId = "";
    private String trustAnchor = "";
    private String dataDir = "";
    private String mode = "observe";
    private final Api api = new Api();
    private final Protection protection = new Protection();
    private final Spool spool = new Spool();

    public static class Api {
        private boolean enabled = true;
        private int observationWindow = 60;
        private int maxAssets = 5_000;
        private List<String> bodySummaryAllowlist = new ArrayList<>();
        private List<String> trustedProxies = new ArrayList<>();
        public boolean isEnabled() { return enabled; } public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getObservationWindow() { return observationWindow; } public void setObservationWindow(int observationWindow) { this.observationWindow = observationWindow; }
        public int getMaxAssets() { return maxAssets; } public void setMaxAssets(int maxAssets) { this.maxAssets = maxAssets; }
        public List<String> getBodySummaryAllowlist() { return bodySummaryAllowlist; } public void setBodySummaryAllowlist(List<String> value) { bodySummaryAllowlist = value; }
        public List<String> getTrustedProxies() { return trustedProxies; } public void setTrustedProxies(List<String> value) { trustedProxies = value; }
    }
    public static class Protection {
        private List<String> enabledRuleIds = new ArrayList<>();
        public List<String> getEnabledRuleIds() { return enabledRuleIds; } public void setEnabledRuleIds(List<String> value) { enabledRuleIds = value; }
    }
    public static class Spool {
        private int maxItems = 10_000; private long maxBytes = 100L * 1024 * 1024;
        public int getMaxItems() { return maxItems; } public void setMaxItems(int value) { maxItems = value; }
        public long getMaxBytes() { return maxBytes; } public void setMaxBytes(long value) { maxBytes = value; }
    }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean value) { enabled = value; }
    public String getServerUrl() { return serverUrl; } public void setServerUrl(String value) { serverUrl = value; }
    public int getAppId() { return appId; } public void setAppId(int value) { appId = value; }
    public String getAppName() { return appName; } public void setAppName(String value) { appName = value; }
    public String getAppVersion() { return appVersion; } public void setAppVersion(String value) { appVersion = value; }
    public String getAppKeyId() { return appKeyId; } public void setAppKeyId(String value) { appKeyId = value; }
    public String getServerKeyId() { return serverKeyId; } public void setServerKeyId(String value) { serverKeyId = value; }
    public String getTrustAnchor() { return trustAnchor; } public void setTrustAnchor(String value) { trustAnchor = value; }
    public String getDataDir() { return dataDir; } public void setDataDir(String value) { dataDir = value; }
    public String getMode() { return mode; } public void setMode(String value) { mode = value; }
    public Api getApi() { return api; } public Protection getProtection() { return protection; } public Spool getSpool() { return spool; }
}
