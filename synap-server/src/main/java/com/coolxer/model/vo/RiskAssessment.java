package com.coolxer.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {
    private String detectorVersion;
    private String ruleVersion;
    private double score;
    private double confidence;
    private List<String> reasonCodes = new ArrayList<>();
    private int evidenceCount;
    private boolean observation;
    private String trustLevel;
}
