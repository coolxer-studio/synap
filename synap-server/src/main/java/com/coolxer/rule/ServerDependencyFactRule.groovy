import com.coolxer.model.agenda.*
def a=new ArrayList<Agenda>()
fact.getVulnerabilities().each{ v -> def level=((v.cvss?:0) as double)>=9?AgendaLevelEnum.HIGH:((v.cvss?:0) as double)>=7?AgendaLevelEnum.MEDIUM:AgendaLevelEnum.LOW; a.add(new Agenda('server-vulnerable-dependency',String.valueOf(v.id)+':'+String.valueOf(v.purl),level)) }
return a
