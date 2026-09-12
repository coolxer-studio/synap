package com.coolxer.service;

import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;

/**
 */
public interface CrossService {

    Object rpc(String id, Header header, Common common);
}
