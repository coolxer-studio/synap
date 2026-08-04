package com.coolxer.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Map;

/**

 */
@Data
@NoArgsConstructor
public class ResponseVO {
    private Integer status;
    private Map<String ,Object> config;
    private ArrayList<ArrayList> agendas;
    private ArrayList<ArrayList> punishes;
    private UploadAck ack;

    public ResponseVO(Integer status, Map<String,Object> config, ArrayList<ArrayList> agendas,
                      ArrayList<ArrayList> punishes) {
        this(status, config, agendas, punishes, null);
    }

    public ResponseVO(Integer status, Map<String,Object> config, ArrayList<ArrayList> agendas,
                      ArrayList<ArrayList> punishes, UploadAck ack) {
        this.status = status;
        this.config = config;
        this.agendas = agendas;
        this.punishes = punishes;
        this.ack = ack;
    }

}
