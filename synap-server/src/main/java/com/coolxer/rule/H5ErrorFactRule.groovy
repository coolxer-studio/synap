package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5ErrorFact

ArrayList<Agenda> agendaList = new ArrayList<>()
H5ErrorFact errorFact = (H5ErrorFact) fact
if ((errorFact.getCount() ?: 1) >= 5) {
    agendaList.add(new Agenda("H5观察-重复前端异常", errorFact.getMessage() + ",count:" + errorFact.getCount(), AgendaLevelEnum.LOW))
}
return agendaList
