package com.pdp.persistence.workspace.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.pdp.shared.error.BusinessRuleException;
import com.pdp.workspace.domain.DataScopeRule;

import java.util.List;

/**
 * 工作空间治理 JSON 编解码器。
 *
 * <p>适配器共享 {@link ObjectMapper} 单例，将 {@code List<String>}（权限键、动作键）与
 * {@code List<DataScopeRule>}（数据范围规则）与数据库 TEXT 列的双向序列化集中在此处，
 * 避免每个适配器重复处理 Jackson 异常与类型引用样板。
 *
 * <p>序列化失败视为不可恢复的数据完整性异常，抛出 {@link BusinessRuleException}
 * （HTTP 422），由全局异常处理器映射为 RFC 7807 Problem Details。
 */
public final class WorkspaceJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter WRITER = MAPPER.writer();
    private static final ObjectReader STRING_LIST_READER =
            MAPPER.readerFor(new TypeReference<List<String>>() {
            });
    private static final ObjectReader RULE_LIST_READER =
            MAPPER.readerFor(new TypeReference<List<DataScopeRule>>() {
            });

    private WorkspaceJsonCodec() {
    }

    /** 序列化字符串列表为 JSON 文本；{@code null} 序列化为 {@code "[]"}，避免数据库列非空约束失败。 */
    public static String writeStringList(List<String> values) {
        try {
            return WRITER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("权限/动作键列表序列化失败: " + e.getMessage());
        }
    }

    /** 反序列化 JSON 文本为字符串列表；{@code null}/空返回空列表。 */
    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return STRING_LIST_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("权限/动作键列表反序列化失败: " + e.getMessage());
        }
    }

    /** 序列化数据范围规则列表为 JSON 文本；{@code null} 序列化为 {@code "[]"}。 */
    public static String writeRuleList(List<DataScopeRule> rules) {
        try {
            return WRITER.writeValueAsString(rules == null ? List.of() : rules);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("数据范围规则列表序列化失败: " + e.getMessage());
        }
    }

    /** 反序列化 JSON 文本为数据范围规则列表；{@code null}/空返回空列表。 */
    public static List<DataScopeRule> readRuleList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return RULE_LIST_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("数据范围规则列表反序列化失败: " + e.getMessage());
        }
    }
}
