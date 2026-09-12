import com.coolxer.model.agenda.*
def p=fact.getPayload(); def a=new ArrayList<Agenda>()
if(String.valueOf(p.capabilityMode).startsWith('degraded')) a.add(new Agenda('server-agent-capability-degraded',String.valueOf(p.capabilityMode),AgendaLevelEnum.LOW))
if((p.inputArguments?:[]).any{String.valueOf(it).contains('jdwp')}) a.add(new Agenda('server-runtime-debugger-enabled','jdwp',AgendaLevelEnum.HIGH))
return a
