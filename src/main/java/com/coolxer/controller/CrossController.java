package com.coolxer.controller;

import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.service.CrossService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.RequestValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

/**
 */
@Slf4j
@RestController
@RequestMapping(value = {"/cross", "2", "/device_index"})
public class CrossController extends AbstractController {

    private final CrossService crossService;
    private final ClientIpResolver clientIpResolver;
    private final RequestValidationService requestValidationService;

    public CrossController(CrossService crossService, ClientIpResolver clientIpResolver,
                           RequestValidationService requestValidationService) {
        this.crossService = crossService;
        this.clientIpResolver = clientIpResolver;
        this.requestValidationService = requestValidationService;
    }

    @PostMapping({"/{id}"})
    public Object crossTarget(@PathVariable("id") String id,@RequestHeader("code") String code,
                              @RequestBody RequestVO requestVO, HttpServletRequest servletRequest) {
        boolean wechat = requestVO != null && requestVO.getCommon() != null && requestVO.getCommon().size() > 8
                && "wechat".equalsIgnoreCase(requestVO.getCommon().get(8));
        if (wechat) {
            String validationError = requestValidationService.validateEnvelope(code, requestVO, servletRequest, false);
            if (validationError != null) return error(validationError, null);
        }
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        String clientIp = clientIpResolver.resolve(servletRequest);
        common.setWanIp("host".equalsIgnoreCase(common.getPlatform())
                ? clientIpResolver.resolveWanIp(common.getWanIp(), clientIp) : clientIp);
        return crossService.rpc(id,header,common);
    }

}
