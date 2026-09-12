package com.coolxer.model.fact.server;
import com.coolxer.model.fact.*; import java.util.Map;
public class ServerAgentHealthFact extends ServerStructuredFact { public ServerAgentHealthFact(String type, Header header, Common common, Map<String,Object> factMap) { super(type, header, common, factMap); } }
