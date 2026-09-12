package com.coolxer.service;


import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;

/**

 */
public interface RuleEngine {

    /**
     * 规则引擎，通过事实（fact）生成议题（agenda）
     *
     * @param fact
     * @return
     */
    public Checkpoint run(AbstractFact fact);
}
