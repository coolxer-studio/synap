package com.coolxer.controller;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.model.punish.Punish;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.Map;

abstract class AbstractController {

    private static final int ERROR_CODE = 0;
    private static final int SUCCEED_CODE = 1;

    protected ResponseVO succeed(Map<String,Object> configMap, ArrayList<Agenda> agendaList, ArrayList<Punish> punishList) {
        ArrayList<ArrayList> agendas = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(agendaList)) {
            for (Agenda agenda : agendaList) {
                agendas.add(agenda.toArrayList());
            }
        }
        ArrayList<ArrayList> punishes = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(punishList)) {
            for (Punish punish : punishList) {
                punishes.add(punish.getAction().toArrayList());
            }
        }
        return new ResponseVO(SUCCEED_CODE, configMap, agendas, punishes);
    }

    protected ResponseVO succeed(ArrayList<Agenda> agenda) {
        return succeed(null, agenda, null);
    }

    protected ResponseVO succeed(ArrayList<Agenda> agendas, ArrayList<Punish> punishes) {
        return succeed(null, agendas, punishes);
    }

    protected ResponseVO error(String msg, Object data) {
        return new ResponseVO(ERROR_CODE, null, null, null);
    }


    protected boolean invalidPlatform(String platform) {
        return !"checker_config".equalsIgnoreCase(platform) && !"punish_config".equalsIgnoreCase(platform);
    }
}
