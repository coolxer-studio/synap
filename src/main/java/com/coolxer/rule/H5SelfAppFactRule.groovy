package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.h5.H5SelfAppFact
import com.coolxer.config.CheckerConfig
import java.net.URI

H5SelfAppFact h5SelfAppFact = (H5SelfAppFact)fact;
ArrayList<Agenda> agendaList = new ArrayList<>()
String domain = h5SelfAppFact.getBase().getDomain();
String host = ""
try { host = domain ? new URI(domain).getHost() : "" } catch (Exception ignored) { }
Map<String, Object> checkerConfig = CheckerConfig.getInstance().getCheckerConfig() ?: [:]
Map<String, Object> configuredAllowlists = (Map<String, Object>) checkerConfig.getOrDefault("selfAppAllowlist", [:])
List<String> allowedDomains = (List<String>) configuredAllowlists.getOrDefault(String.valueOf(h5SelfAppFact.getCommon().getAppId()), [])
if (allowedDomains.isEmpty()) allowedDomains = [h5SelfAppFact.getCommon().getAppPackage()]
boolean allowed = allowedDomains.any { configured -> host.equalsIgnoreCase(configured) }
if(domain.length() == 0 || !allowed){
    agendaList.add(new Agenda("域名欺诈",domain, AgendaLevelEnum.HIGH));
}
return agendaList
