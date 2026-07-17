package com.pdp.workflow.model;

import java.util.Objects;

/**
 * 流程定义内容值对象。
 *
 * <p>封装 BPMN 2.0.2 XML 资源及其内容哈希，用于校验与部署。
 * 哈希算法为 SHA-256，hex 编码。
 *
 * @param bpmnXml     BPMN 2.0.2 XML 文本
 * @param contentHash 内容哈希（SHA-256 hex）
 */
public record WorkflowDefinitionContent(String bpmnXml, String contentHash) {

    /** BPMN XML 最小长度（防止空内容通过校验）。 */
    public static final int MIN_XML_LENGTH = 50;

    public WorkflowDefinitionContent {
        Objects.requireNonNull(bpmnXml, "bpmnXml 不能为 null");
        if (bpmnXml.length() < MIN_XML_LENGTH) {
            throw new IllegalArgumentException(
                    "bpmnXml 长度必须 >= " + MIN_XML_LENGTH + "，实际为 " + bpmnXml.length());
        }
        Objects.requireNonNull(contentHash, "contentHash 不能为 null");
        if (contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash 不能为空白");
        }
    }

    public static WorkflowDefinitionContent of(String bpmnXml, String contentHash) {
        return new WorkflowDefinitionContent(bpmnXml, contentHash);
    }
}
