package com.coolxer.model.checkpoint;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.punish.Punish;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.coolxer.model.vo.RiskAssessment;

import java.util.ArrayList;
import java.util.HashSet;

/**

 */

@Getter
public class Checkpoint {

    private AbstractFact fact;
    private String rule;
    private ArrayList<Agenda> agendas;
    private HashSet<Punish> punishes;
    private String serverTime;
    private RiskAssessment risk;

    public Checkpoint(AbstractFact fact, String rule, ArrayList<Agenda> agendas, HashSet<Punish> punishes, String serverTime) {
        this(fact, rule, agendas, punishes, serverTime, null);
    }

    public Checkpoint(AbstractFact fact, String rule, ArrayList<Agenda> agendas, HashSet<Punish> punishes,
                      String serverTime, RiskAssessment risk) {
        this.fact = fact; this.rule = rule; this.agendas = agendas; this.punishes = punishes;
        this.serverTime = serverTime; this.risk = risk;
    }

}
