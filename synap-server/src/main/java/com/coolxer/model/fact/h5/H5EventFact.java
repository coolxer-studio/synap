package com.coolxer.model.fact.h5;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;

import java.util.Map;

/**

 */
@Getter
public class H5EventFact extends AbstractFact {

    private String href;
    private String docTitle;
    private Integer online;
    private Map<String,Object> data;


    public H5EventFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String) factMap.get("buildDate"), header, common);
        href = (String) factMap.get("href");
        docTitle = (String) factMap.get("docTitle");
        online = (Integer) factMap.get("online");
        data = (Map<String,Object>) factMap.get("data");
    }
}
