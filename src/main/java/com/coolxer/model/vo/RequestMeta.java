package com.coolxer.model.vo;

import lombok.Data;

@Data
public class RequestMeta {
    private Integer protocolVersion;
    private String batchId;
    private String sessionId;
    private Integer attempt;
    private Long createdAt;
    private String detectorVersion;
    private String appKeyId;

    public boolean isValidStart() {
        return protocolVersion != null && protocolVersion == 1
                && validAppKeyId() && detectorVersion != null && !detectorVersion.isBlank()
                && detectorVersion.length() <= 64;
    }

    public boolean isValidFor(long startId) {
        return isValidStart() && batchId != null && !batchId.isBlank() && batchId.length() <= 128
                && sessionId != null && sessionId.equals(String.valueOf(startId))
                && attempt != null && attempt > 0 && createdAt != null && createdAt > 0;
    }

    private boolean validAppKeyId() {
        return appKeyId != null && appKeyId.matches("[a-zA-Z0-9_-]{1,64}");
    }
}
