package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.host.HostAuditFact

HostAuditFact host = (HostAuditFact) fact
ArrayList<Agenda> agendas = new ArrayList<>()
Map<String, Integer> loginFailures = new HashMap<>()

host.getEvents()?.each { raw ->
    if (!(raw instanceof List) || raw.size() != HostAuditFact.EVENT_COLUMNS) return
    List row = (List) raw
    String category = String.valueOf(row[2])
    String action = String.valueOf(row[3])
    String result = String.valueOf(row[4])
    String actor = String.valueOf(row[5])
    String target = String.valueOf(row[7])
    String remote = String.valueOf(row[11])
    String detail = String.valueOf(row[17])
    String source = actor + "|" + target

    if (category == "session" && action == "login" && result == "failure") {
        String key = actor + "@" + remote
        loginFailures.put(key, loginFailures.getOrDefault(key, 0) + 1)
    }
    if (category == "account" && action == "permission_changed") {
        agendas.add(new Agenda("host-audit-privileged-account-change", source, AgendaLevelEnum.HIGH))
    }
    if (category == "config_file" && ["created", "modified", "deleted"].contains(action)) {
        agendas.add(new Agenda("host-audit-critical-config-change", target, AgendaLevelEnum.HIGH))
    }
    if (category == "peripheral" && action == "attached") {
        agendas.add(new Agenda("host-audit-unknown-usb", target, AgendaLevelEnum.MEDIUM))
    }
    if (category == "scheduled_task" && ["created", "modified"].contains(action)
            && (target ==~ /(?i).*(tmp|temp|powershell|cmd\.exe|curl|wget|osascript|base64).*/
                || detail ==~ /(?i).*(encodedcommand|downloadstring|bypass).*/)) {
        agendas.add(new Agenda("host-audit-suspicious-task", target, AgendaLevelEnum.HIGH))
    }
    if (category == "capability" && action == "degraded") {
        agendas.add(new Agenda("host-audit-capability-degraded", String.valueOf(row[15]), AgendaLevelEnum.MEDIUM))
    }
}

loginFailures.each { key, count ->
    if (count >= 3) agendas.add(new Agenda("host-audit-repeated-login-failure", key, AgendaLevelEnum.MEDIUM))
}
return agendas
