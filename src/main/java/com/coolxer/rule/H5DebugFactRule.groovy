package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5DebugFact

H5DebugFact h5DebugFact = (H5DebugFact)fact;

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.add(new Agenda("调试","type:"+h5DebugFact.getDebugType(), AgendaLevelEnum.HIGH));
return agendaList