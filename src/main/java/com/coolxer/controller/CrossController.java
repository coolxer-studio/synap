package com.coolxer.controller;

import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.service.CrossService;
import com.coolxer.service.ClientIpResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 */
@Slf4j
@RestController
@RequestMapping(value = {"/cross","2"})
public class CrossController extends AbstractController {

    private final CrossService crossService;
    private final ClientIpResolver clientIpResolver;

    public CrossController(CrossService crossService, ClientIpResolver clientIpResolver) {
        this.crossService = crossService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping({"/{id}"})
    public Object crossTarget(@PathVariable("id") String id,@RequestHeader("code") String code,
                              @RequestBody RequestVO requestVO, HttpServletRequest servletRequest) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        common.setWanIp(clientIpResolver.resolve(servletRequest));
        return crossService.rpc(id,header,common);
    }

}
