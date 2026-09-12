package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.android.AndroidNetFact

ArrayList<Agenda> agendaList = new ArrayList<>()

AndroidNetFact androidNetFact = (AndroidNetFact) fact;

if(androidNetFact.getCustom()!=null){
    if((Boolean)androidNetFact.getCustom().get("multiSocket")){
        agendaList.add(new Agenda("应用多开", "multiSocket", AgendaLevelEnum.MEDIUM));
    }
}

for (String cert:androidNetFact.getCertificate()){
    if(cert.contains("HttpCanary")){
        agendaList.add(new Agenda("HttpCanary证书", cert, AgendaLevelEnum.HIGH));
    }else{
        agendaList.add(new Agenda("异常证书", cert, AgendaLevelEnum.MEDIUM));
    }
}

if(androidNetFact.getHosts().size()>0){
    agendaList.add(new Agenda("不合规域名配置", androidNetFact.getHosts().join("\n"), AgendaLevelEnum.MEDIUM));
}

if(androidNetFact.getHttpProxy().size()>0){
    agendaList.add(new Agenda("Http代理", androidNetFact.getHttpProxy().join("\n"), AgendaLevelEnum.HIGH));
}

for (String netInterface:androidNetFact.getNetInterface()){
    if(netInterface.contains("tun0:tun0")){
        agendaList.add(new Agenda("VPN通信", netInterface, AgendaLevelEnum.HIGH));
    }
}

if(androidNetFact.getSslPing().size()>0){
    agendaList.add(new Agenda("SSL劫持", androidNetFact.getSslPing().join("\n"), AgendaLevelEnum.HIGH));
}

for (String udsString:androidNetFact.getUds()){
    if(udsString.contains("frida")){
        agendaList.add(new Agenda("frida-uds", udsString, AgendaLevelEnum.HIGH));
    }
}

for (String port:androidNetFact.getPort()){
    if(port == "8811" || port == "8813"){
        agendaList.add(new Agenda("疑似远控", port, AgendaLevelEnum.MEDIUM));
    }
}

for (String socket:androidNetFact.getSocket()){
    if(socket == "27042" || socket == "27043"){
        agendaList.add(new Agenda("frida通信", socket, AgendaLevelEnum.HIGH));
    }
    if(socket == "31415"){
        agendaList.add(new Agenda("Drozer通信", socket, AgendaLevelEnum.HIGH));
    }
    if(socket == "23946"){
        agendaList.add(new Agenda("IDA通信", socket, AgendaLevelEnum.HIGH));
    }
}

return agendaList