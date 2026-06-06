package com.coolxer.controller;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.android.AndroidStartFact;
import com.coolxer.model.fact.h5.H5StartFact;
import com.coolxer.model.fact.host.HostStartFact;
import com.coolxer.model.fact.ios.IosStartFact;
import com.coolxer.model.fact.wechat.WechatStartFact;
import com.coolxer.model.punish.Punish;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.impl.RuleEngineImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/**
 * 数据通信核心接口
 */
@Slf4j
@RestController
@RequestMapping(value = {"/attach","1"})
public class AttachController extends AbstractController {
    private final RuleEngineImpl ruleEngineImpl;
    private final CheckerConfigService checkerConfigService;

    public AttachController(RuleEngineImpl ruleEngineImpl, CheckerConfigService checkerConfigService) {
        this.ruleEngineImpl = ruleEngineImpl;
        this.checkerConfigService = checkerConfigService;
    }

    @RequestMapping(value = {"start","/1"}, method =RequestMethod.POST)
    public ResponseVO start(@RequestHeader("code") String code, @RequestBody RequestVO requestVO) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());

        ArrayList<Punish> punishList = new ArrayList<>();
        ArrayList<Agenda> agendaList = new ArrayList<>();

        String platform = common.getPlatform();
        // 从服务中获取最新的配置
        Map<String,Object> config = checkerConfigService.getConfigByPlatform(platform);
        if (config == null) {
            return error("未找到平台配置: " + platform, common);
        }
        
        AbstractFact fact = null;
        if("ios".equals(platform)){
            fact = new IosStartFact("StartData",header,common,config);
        }else if("android".equals(platform)){
            fact = new AndroidStartFact("StartData",header,common,config);
        }else if("h5".equals(platform)){
            fact = new H5StartFact("StartData",header,common,config);
        }else if("wechat".equals(platform)){
            fact = new WechatStartFact("StartData",header,common,config);
        }else if("host".equals(platform)){
            fact = new HostStartFact("StartData",header,common,config);
        }else {
            return error("unknow platform:"+platform,common);
        }

        if (Objects.nonNull(fact)){
            Checkpoint checkpoint = ruleEngineImpl.run(fact);
            agendaList.addAll(checkpoint.getAgendas());
            //TODO :需要对getPunishes的hashset进行优先级排序，客户端按顺序执行即可
            punishList.addAll(checkpoint.getPunishes());
        }
        return succeed(config,agendaList,punishList);

    }

    @RequestMapping(value = {"upload","/2"}, method = RequestMethod.POST)
    public Object upload(@RequestHeader("code") String code, @RequestBody RequestVO requestVO) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());

        ArrayList<Punish> punishList = new ArrayList<>();
        ArrayList<Agenda> agendaList = new ArrayList<>();

        for (Map<String,Object> itemMap: requestVO.getData()) {
            if(itemMap == null){
                // TODO 不应该有null
                continue;
            }
            AbstractFact fact = FactBuilder.buildFact(header,common,itemMap);
            if (Objects.nonNull(fact)){
                Checkpoint checkpoint = ruleEngineImpl.run(fact);
                if(checkpoint != null){
                    agendaList.addAll(checkpoint.getAgendas());
                    punishList.addAll(checkpoint.getPunishes());
                }
            }
        }
        return succeed(agendaList,punishList);
    }




}
