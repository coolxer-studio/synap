package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostDeviceFact

HostDeviceFact host = (HostDeviceFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
def storage = host.getStorage()
if (storage != null && storage.getTotalDisk() != null && storage.getTotalDisk() > 0 && storage.getUsedDisk() / storage.getTotalDisk() >= 0.90d) {
    agendas.add(new Agenda("host-resource-pressure", "disk", AgendaLevelEnum.MEDIUM))
}
if (storage != null && storage.getTotalRam() != null && storage.getTotalRam() > 0 && storage.getUsedRam() / storage.getTotalRam() >= 0.95d) {
    agendas.add(new Agenda("host-resource-pressure", "memory", AgendaLevelEnum.MEDIUM))
}
return agendas
