package com.coolxer.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**

 */
@Data
public class RequestVO {
    private List<String> common;
    private RequestMeta meta;
    private ArrayList<Map<String,Object>> data;
}
