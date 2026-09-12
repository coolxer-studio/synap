import com.coolxer.model.agenda.*
def p=fact.getPayload(); def a=new ArrayList<Agenda>()
if(String.valueOf(p.status)!='running'||((p.dropped?:0) as long)>0) a.add(new Agenda('server-agent-health-degraded',String.valueOf(p.status),AgendaLevelEnum.MEDIUM))
return a
