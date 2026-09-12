package com.coolxer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WechatRouteContractTest {

    @Test
    void exposesFrozenWechatRouteAliases() {
        RequestMapping attach = AttachController.class.getAnnotation(RequestMapping.class);
        RequestMapping cross = CrossController.class.getAnnotation(RequestMapping.class);

        assertTrue(Arrays.asList(attach.value()).contains("/risk"));
        assertTrue(Arrays.asList(cross.value()).contains("/device_index"));
    }
}
