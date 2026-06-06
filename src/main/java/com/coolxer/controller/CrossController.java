package com.coolxer.controller;

import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.service.CrossService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 */
@Slf4j
@RestController
@RequestMapping(value = {"/cross","2"})
public class CrossController extends AbstractController {

    private final CrossService crossService;

    public CrossController(CrossService crossService) {
        this.crossService = crossService;
    }

    @PostMapping({"/{id}"})
    public Object crossTarget(@PathVariable("id") String id,@RequestHeader("code") String code, @RequestBody RequestVO requestVO) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        return crossService.rpc(id,header,common);
    }

}
