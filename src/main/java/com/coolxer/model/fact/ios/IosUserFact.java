package com.coolxer.model.fact.ios;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class IosUserFact extends AbstractFact {

    private String userId;

    private Map<String,Object> data;

    public IosUserFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        userId = (String)factMap.get("userId");
        data = (Map<String,Object>)factMap.get("data");
    }

}
