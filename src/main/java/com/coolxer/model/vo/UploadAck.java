package com.coolxer.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UploadAck {
    private String batchId;
    private List<String> acceptedMessageIds = new ArrayList<>();
    private List<String> duplicateMessageIds = new ArrayList<>();
    private List<RejectedItem> rejectedItems = new ArrayList<>();

    public UploadAck(String batchId) {
        this.batchId = batchId;
    }
}
