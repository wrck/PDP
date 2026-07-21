package com.pdp.domainconfig.domain.metamodel;

/**
 * 核心字段来源（FR-173 平台版本化核心术语目录）。
 *
 * <p>每个核心字段必须声明其标准来源或 PDP 扩展标记，便于审计追溯。
 */
public enum CoreFieldSource {
    ISO_21500,
    PMI_LEXICON,
    BPMN_2_0_2,
    PDP_EXTENSION
}
