package com.coolxer.model.checkpoint;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.punish.Punish;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashSet;

/**

 */

@Getter
@AllArgsConstructor
public class Checkpoint {

    private AbstractFact fact;
    private String rule;
    private ArrayList<Agenda> agendas;
    private HashSet<Punish> punishes;
    private String serverTime;

}
