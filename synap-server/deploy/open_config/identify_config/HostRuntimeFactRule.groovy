package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostRuntimeFact

HostRuntimeFact host = (HostRuntimeFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
host.getProcess()?.findAll { name -> name != null && !String.valueOf(name).isBlank() }
        ?.each { name -> agendas.add(new Agenda("host-suspicious-process", String.valueOf(name), AgendaLevelEnum.HIGH)) }
host.getEnv()?.each { key, value ->
    if (value != null && !String.valueOf(value).isBlank()) {
        AgendaLevelEnum level = String.valueOf(key).contains("PROXY") ? AgendaLevelEnum.LOW : AgendaLevelEnum.MEDIUM
        agendas.add(new Agenda("host-runtime-environment", String.valueOf(key), level))
    }
}
return agendas
