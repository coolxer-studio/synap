package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5LocationFact

ArrayList<Agenda> agendaList = new ArrayList<>()
H5LocationFact locationFact = (H5LocationFact) fact
if (locationFact.getBase() != null && locationFact.getBase().getSpeed() > 100) {
    agendaList.add(new Agenda("H5观察-异常移动速度", "speed:" + locationFact.getBase().getSpeed(), AgendaLevelEnum.LOW))
}
return agendaList
