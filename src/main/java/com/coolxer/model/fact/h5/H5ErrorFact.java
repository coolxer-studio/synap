package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class H5ErrorFact extends AbstractFact {
    private String from;
    private String message;
    private String source;
    private Integer lineNo;
    private Integer colNo;
    private Integer count;

    public H5ErrorFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        from = (String)factMap.get("from");
        message = (String)factMap.get("message");
        source = (String)factMap.get("source");
        lineNo = (Integer)factMap.get("lineno");
        colNo = (Integer)factMap.get("colno");
        count = factMap.get("count") instanceof Number ? ((Number) factMap.get("count")).intValue() : 1;
    }

}
