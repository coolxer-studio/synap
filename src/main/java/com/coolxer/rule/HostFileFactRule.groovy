package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostFileFact

HostFileFact host = (HostFileFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
host.getExist()?.each { path -> agendas.add(new Agenda("host-file-indicator", String.valueOf(path), AgendaLevelEnum.HIGH)) }
host.getFileList()?.each { key, values ->
    if (values instanceof Collection && !values.isEmpty()) {
        agendas.add(new Agenda("host-file-indicator", String.valueOf(key), AgendaLevelEnum.HIGH))
    }
}
return agendas
