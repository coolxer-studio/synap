package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostNetFact

HostNetFact host = (HostNetFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
host.getPort()?.each { port -> agendas.add(new Agenda("host-suspicious-port", String.valueOf(port), AgendaLevelEnum.HIGH)) }
host.getSocket()?.each { port -> agendas.add(new Agenda("host-suspicious-port", String.valueOf(port), AgendaLevelEnum.HIGH)) }
if (host.getHttpProxy()?.any { value -> value != null && !String.valueOf(value).isBlank() }) {
    agendas.add(new Agenda("host-proxy-enabled", "configured", AgendaLevelEnum.LOW))
}
host.getNetInterface()?.findAll { name -> name != null && !String.valueOf(name).isBlank() }
        ?.each { name -> agendas.add(new Agenda("host-network-interface", String.valueOf(name), AgendaLevelEnum.LOW)) }
return agendas
