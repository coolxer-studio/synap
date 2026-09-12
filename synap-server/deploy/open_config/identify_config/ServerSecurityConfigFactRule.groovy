import com.coolxer.model.agenda.*
def p=fact.getPayload(); def a=new ArrayList<Agenda>()
if(String.valueOf(p.managementEndpointsExposed).contains('*')) a.add(new Agenda('server-management-endpoint-wildcard-exposure','management-endpoints',AgendaLevelEnum.HIGH))
if(Boolean.TRUE.equals(p.sessionConfigured) && !Boolean.TRUE.equals(p.csrfConfigured)) a.add(new Agenda('server-session-csrf-disabled','spring-security',AgendaLevelEnum.HIGH))
return a
