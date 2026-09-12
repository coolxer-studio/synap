package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.fact.h5.H5StartFact

ArrayList<Agenda> agendaList = new ArrayList<>()
String lanIp = ((H5StartFact)fact).getCommon().getLanIp();
return agendaList