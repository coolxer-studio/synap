package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class IosStartFact extends AbstractFact {

    private Map<String,Object> config;

    public IosStartFact(String type, Header header, Common common, Map<String,Object> config) {
        super(type,common.getClientTime(), header, common);
        this.config=config;
    }

}
