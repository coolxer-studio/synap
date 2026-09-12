package com.coolxer.model.fact.android;

import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**

 */
@Slf4j
@Getter
public class AndroidAnrFact extends AbstractFact {
    private String fileName;
    private String lastModified;
    private String content;

    public AndroidAnrFact(String type, Header header, Common common, Map<String,Object> factMap) {
        super(type,(String)factMap.get("buildDate"), header, common);
        fileName = (String) factMap.get("fileName");
        lastModified = (String)factMap.get("lastModified");
        content = (String)factMap.get("content");
    }

}
