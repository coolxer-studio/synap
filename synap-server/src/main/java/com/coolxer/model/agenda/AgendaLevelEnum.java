package com.coolxer.model.agenda;


import lombok.Getter;

/**
 * agenda的等级枚举类
 *
 *

 */
@Getter
public enum AgendaLevelEnum {

  /**
   * 高风险
   */
  HIGH("high", "高风险"),

  /**
   * 中风险
   */
  MEDIUM("medium", "中风险"),

  /**
   * 低风险
   */
  LOW("low", "低风险"),

  /**
   * 常规
   */
  NORMAL("normal", "常规"),

  /**
   * 其他
   */
  OTHER("other", "其他");

  private final String code;
  private final String description;

  AgendaLevelEnum(String code, String description) {
    this.code = code;
    this.description = description;
  }


}
