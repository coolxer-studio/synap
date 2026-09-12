package com.coolxer.model.fact.wechat;

import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import java.util.Map;

public class WechatActionFact extends WechatMapFact {
    public WechatActionFact(String type, Header header, Common common, Map<String, Object> factMap) {
        super(type, header, common, factMap);
    }
}
