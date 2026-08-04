package com.coolxer.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RejectedItem {
    private String messageId;
    private boolean retryable;
    private String reason;
}
