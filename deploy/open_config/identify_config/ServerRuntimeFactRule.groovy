import com.coolxer.model.agenda.*
def p=fact.getPayload(); def a=new ArrayList<Agenda>()
if((p.deadlockedThreadCount?:0) as long) a.add(new Agenda('server-runtime-deadlocked-threads',String.valueOf(p.deadlockedThreadCount),AgendaLevelEnum.HIGH))
if((p.heapCommitted?:0) as long>0 && (p.heapUsed?:0) as double/(p.heapCommitted as double)>0.9) a.add(new Agenda('server-runtime-memory-pressure','over-90-percent',AgendaLevelEnum.MEDIUM))
return a
