package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidAnrFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidAnrFact androidAnrFact = (AndroidAnrFact) fact;
agendaList.add(new Agenda("应用无响应", androidAnrFact.fileName, AgendaLevelEnum.LOW));

return agendaList