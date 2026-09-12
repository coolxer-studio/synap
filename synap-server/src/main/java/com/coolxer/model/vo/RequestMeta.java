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
    private String messageId;
    private Long observedAt;
    private Long sequence;

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

    public boolean hasStartTracking() {
        return messageId != null || batchId != null || sessionId != null || attempt != null
                || createdAt != null || observedAt != null || sequence != null;
    }

    public boolean isValidStartTracking(long startId) {
        return validId(messageId) && validId(batchId)
                && sessionId != null && sessionId.equals(String.valueOf(startId))
                && attempt != null && attempt > 0
                && createdAt != null && createdAt > 0
                && observedAt != null && observedAt > 0
                && sequence != null && sequence == 1;
    }

    private boolean validId(String value) {
        return value != null && !value.isBlank() && value.length() <= 128;
    }

    private boolean validAppKeyId() {
        return appKeyId != null && appKeyId.matches("[a-zA-Z0-9_-]{1,64}");
    }
}
