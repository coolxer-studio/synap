package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostEventFact

HostEventFact host = (HostEventFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
host.getSshd()?.each { login ->
    if (login instanceof List && !login.isEmpty()) {
        String user = String.valueOf(login[0])
        agendas.add(new Agenda(user == "root" ? "host-ssh-root-login" : "host-ssh-login", user, user == "root" ? AgendaLevelEnum.MEDIUM : AgendaLevelEnum.LOW))
    }
}
return agendas
