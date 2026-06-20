package com.coolxer.model.fact.host;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class HostStartFact extends AbstractFact {

    private Map<String,Object> config;

    public HostStartFact(String type, Header header, Common common, Map<String,Object> config) {
        super(type,common.getClientTime(), header, common);
        this.config=config;
    }

}
