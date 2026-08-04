package com.coolxer.model.vo;

import lombok.Data;

@Data
public class RequestMeta {
    private Integer protocolVersion;
    private String batchId;
    private String sessionId;
    private Integer attempt;
    private Long createdAt;

    public boolean isV2() {
        return protocolVersion != null && protocolVersion >= 2;
    }

    public boolean isValidFor(long startId) {
        return isV2() && batchId != null && !batchId.isBlank() && batchId.length() <= 128
                && sessionId != null && sessionId.equals(String.valueOf(startId))
                && attempt != null && attempt > 0 && createdAt != null && createdAt > 0;
    }
}
