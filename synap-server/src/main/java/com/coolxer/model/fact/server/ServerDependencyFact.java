package com.coolxer.model.fact.server;
import com.coolxer.model.fact.*; import java.util.*;
public class ServerDependencyFact extends ServerStructuredFact {
    private List<Map<String,Object>> vulnerabilities = List.of();
    public ServerDependencyFact(String type, Header header, Common common, Map<String,Object> factMap) { super(type, header, common, factMap); }
    public List<Map<String,Object>> getDependencies() { Object value = getPayload().get("dependencies"); return value instanceof List<?> list ? (List<Map<String,Object>>)(List<?>)list : List.of(); }
    public List<Map<String,Object>> getVulnerabilities() { return vulnerabilities; }
    public void setVulnerabilities(List<Map<String,Object>> values) { vulnerabilities = values == null ? List.of() : List.copyOf(values); }
}
