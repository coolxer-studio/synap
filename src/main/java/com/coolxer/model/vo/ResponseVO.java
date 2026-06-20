package com.coolxer.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@AllArgsConstructor
@Data
public class ResponseVO {
    private Integer status;
    private Map<String ,Object> config;
    private ArrayList<ArrayList> agendas;
    private ArrayList<ArrayList> punishes;

}
