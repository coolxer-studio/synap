package com.coolxer.rule

import com.coolxer.model.agenda.Agenda
import com.coolxer.model.agenda.AgendaLevelEnum
import com.coolxer.model.fact.ios.IosNetFact
import org.apache.commons.lang3.StringUtils

def frameForFridaWithPort(IosNetFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getSocket().contains(27042)){
        agendaList.addAll(new Agenda("frida","27042端口启用", AgendaLevelEnum.MEDIUM))
    }
    return agendaList
}

def httpProxy(IosNetFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String httpProxy : fact.getHttpProxy()){
        if(StringUtils.isNotEmpty(httpProxy)){
            agendaList.add(new Agenda("httpProxy",httpProxy,AgendaLevelEnum.MEDIUM))
        }
    }
    return agendaList
}

def vpnWithNetName(IosNetFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    for (String netName : fact.getNetInterface()){
        if(netName.contains("tap")||netName.contains("tun")||netName.contains("ipsec")||netName.contains("ppp")){
            agendaList.addAll(new Agenda("vpn", StringUtils.join(fact.getNetInterface(),";"),AgendaLevelEnum.MEDIUM))
        }
    }
    return agendaList
}

def sslPing(IosNetFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getSslPing().size()>0){
        agendaList.add(new Agenda("sslPing", StringUtils.join(fact.getSslPing(),";")))
    }
    return agendaList
}

def hostsWithFraud(IosNetFact fact){
    ArrayList<Agenda> agendaList = new ArrayList<>()
    if(fact.getHosts().size()>0){
        agendaList.add(new Agenda("hostsFraud", StringUtils.join(fact.getHosts(),";")))
    }
    return agendaList
}

ArrayList<Agenda> agendaList = new ArrayList<>()
agendaList.addAll(vpnWithNetName(fact))
agendaList.addAll(httpProxy(fact))
agendaList.addAll(frameForFridaWithPort(fact))
agendaList.addAll(sslPing(fact))
agendaList.addAll(hostsWithFraud(fact))
return agendaList