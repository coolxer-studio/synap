package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5UserFact

ArrayList<Agenda> agendaList = new ArrayList<>()
H5UserFact userFact = (H5UserFact) fact
if (userFact.getUserId() == null || userFact.getUserId().isBlank()) {
    agendaList.add(new Agenda("H5观察-缺失用户标识", "empty userId", AgendaLevelEnum.LOW))
}
return agendaList
