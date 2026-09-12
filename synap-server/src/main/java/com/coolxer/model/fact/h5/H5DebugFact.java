package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class H5DebugFact extends AbstractFact {

    private Integer debugType;
    private Integer diff;

    public H5DebugFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        debugType = (Integer) factMap.get("debugType");
        diff = (Integer) factMap.get("diff");
    }

}
