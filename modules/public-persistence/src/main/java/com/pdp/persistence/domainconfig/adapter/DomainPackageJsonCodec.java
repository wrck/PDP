package com.pdp.persistence.domainconfig.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.pdp.domainconfig.domain.packageversion.CompatibilityLevel;
import com.pdp.domainconfig.domain.packageversion.CompatibilityStatement;
import com.pdp.domainconfig.domain.packageversion.ValidationItem;
import com.pdp.shared.error.BusinessRuleException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 领域包治理 JSON 编解码器。
 *
 * <p>适配器共享 {@link ObjectMapper} 单例，集中处理以下序列化场景：
 * <ul>
 *   <li>{@code List<String>}（声明权限键）↔ JSON；</li>
 *   <li>{@code Set<String>}（核心字段别名）↔ JSON；</li>
 *   <li>{@code Map<String, String>}（流程变量映射）↔ JSON；</li>
 *   <li>{@link CompatibilityStatement}（兼容性声明）↔ JSON；</li>
 *   <li>{@code List<ValidationItem>}（校验项列表）↔ JSON。</li>
 * </ul>
 *
 * <p>序列化失败视为不可恢复的数据完整性异常，抛出 {@link BusinessRuleException}（HTTP 422）。
 */
public final class DomainPackageJsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectWriter WRITER = MAPPER.writer();
    private static final ObjectReader STRING_LIST_READER =
            MAPPER.readerFor(new TypeReference<List<String>>() {
            });
    private static final ObjectReader STRING_SET_READER =
            MAPPER.readerFor(new TypeReference<Set<String>>() {
            });
    private static final ObjectReader STRING_MAP_READER =
            MAPPER.readerFor(new TypeReference<Map<String, String>>() {
            });
    private static final ObjectReader VALIDATION_ITEM_LIST_READER =
            MAPPER.readerFor(new TypeReference<List<ValidationItem>>() {
            });

    private DomainPackageJsonCodec() {
    }

    // ============================================================
    // List<String>
    // ============================================================

    public static String writeStringList(List<String> values) {
        try {
            return WRITER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串列表序列化失败: " + e.getMessage());
        }
    }

    public static List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return STRING_LIST_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串列表反序列化失败: " + e.getMessage());
        }
    }

    // ============================================================
    // Set<String>
    // ============================================================

    public static String writeStringSet(Set<String> values) {
        try {
            return WRITER.writeValueAsString(values == null ? Set.of() : values);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串集合序列化失败: " + e.getMessage());
        }
    }

    public static Set<String> readStringSet(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            return STRING_SET_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串集合反序列化失败: " + e.getMessage());
        }
    }

    // ============================================================
    // Map<String, String>
    // ============================================================

    public static String writeStringMap(Map<String, String> values) {
        try {
            return WRITER.writeValueAsString(values == null ? Map.of() : values);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串映射序列化失败: " + e.getMessage());
        }
    }

    public static Map<String, String> readStringMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return STRING_MAP_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("字符串映射反序列化失败: " + e.getMessage());
        }
    }

    // ============================================================
    // CompatibilityStatement
    // ============================================================

    public static String writeCompatibilityStatement(CompatibilityStatement statement) {
        if (statement == null) {
            return null;
        }
        try {
            return WRITER.writeValueAsString(statement);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("兼容性声明序列化失败: " + e.getMessage());
        }
    }

    public static CompatibilityStatement readCompatibilityStatement(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, CompatibilityStatement.class);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("兼容性声明反序列化失败: " + e.getMessage());
        }
    }

    // ============================================================
    // List<ValidationItem>
    // ============================================================

    public static String writeValidationItemList(List<ValidationItem> items) {
        try {
            return WRITER.writeValueAsString(items == null ? List.of() : items);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("校验项列表序列化失败: " + e.getMessage());
        }
    }

    public static List<ValidationItem> readValidationItemList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return VALIDATION_ITEM_LIST_READER.readValue(json);
        } catch (JsonProcessingException e) {
            throw new BusinessRuleException("校验项列表反序列化失败: " + e.getMessage());
        }
    }

    // ============================================================
    // 辅助：兼容性声明的快速构造（数据库 JSON 列缺省值）
    // ============================================================

    /** 创建一个 PATCH_ONLY 级别的默认兼容性声明（用于初始化草稿）。 */
    public static CompatibilityStatement defaultPatchOnly(Instant now) {
        return new CompatibilityStatement(
                CompatibilityLevel.PATCH_ONLY,
                "默认 PATCH_ONLY 兼容性声明",
                null,
                null);
    }
}
