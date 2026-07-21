package com.pdp.domainconfig.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.pdp.domainconfig.domain.behavior.AuditLevel;
import com.pdp.domainconfig.domain.behavior.DomainPackageWorkflowBinding;
import com.pdp.domainconfig.domain.behavior.ExecutionIdentity;
import com.pdp.domainconfig.domain.behavior.ExtensionDefinition;
import com.pdp.domainconfig.domain.behavior.ExtensionFailurePolicy;
import com.pdp.domainconfig.domain.behavior.ExtensionIsolationPolicy;
import com.pdp.domainconfig.domain.behavior.InstanceMigrationPolicy;
import com.pdp.domainconfig.domain.behavior.OverrideDefinition;
import com.pdp.domainconfig.domain.behavior.PermissionDefinition;
import com.pdp.domainconfig.domain.behavior.RuleAction;
import com.pdp.domainconfig.domain.behavior.RuleActionType;
import com.pdp.domainconfig.domain.behavior.RuleDefinition;
import com.pdp.domainconfig.domain.behavior.RuleMode;
import com.pdp.domainconfig.domain.behavior.TopLifecycleState;
import com.pdp.domainconfig.domain.behavior.WorkflowBindingTrigger;
import com.pdp.domainconfig.domain.manifest.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.Cardinality;
import com.pdp.domainconfig.domain.metamodel.CoreFieldSource;
import com.pdp.domainconfig.domain.metamodel.DataType;
import com.pdp.domainconfig.domain.metamodel.FieldDefinition;
import com.pdp.domainconfig.domain.metamodel.IndexMode;
import com.pdp.domainconfig.domain.metamodel.LocalizedText;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectKind;
import com.pdp.domainconfig.domain.metamodel.PageDefinition;
import com.pdp.domainconfig.domain.metamodel.RelationDefinition;
import com.pdp.domainconfig.domain.metamodel.RelationOwnership;
import com.pdp.domainconfig.domain.metamodel.UniqueScope;
import com.pdp.domainconfig.domain.metamodel.ViewDefinition;
import com.pdp.domainconfig.domain.metamodel.ViewType;
import com.pdp.domainconfig.domain.packageversion.CoreFieldReuseDisposition;
import com.pdp.domainconfig.domain.packageversion.DomainPackageCoreFieldReuse;
import com.pdp.domainconfig.domain.packageversion.DomainPackageLayer;
import com.pdp.domainconfig.domain.packageversion.PrincipalRef;
import com.pdp.domainconfig.domain.packageversion.PrincipalType;
import com.pdp.shared.error.BusinessRuleException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 领域包清单（manifest）解析器。
 *
 * <p>将 {@code DomainPackageVersion#manifestJson} 字符串解析为 {@link DomainPackageManifest} 内存表示，
 * 供 {@link DomainPackageValidationService} 校验。
 *
 * <p>使用 Jackson {@link JsonNode} 树模型手动解析，避免 record 字段名与 JSON Schema 关键字
 * （如 {@code extends}）冲突，并集中处理默认值与类型转换。
 *
 * <p>解析失败抛出 {@link BusinessRuleException}（HTTP 422），由校验服务转为 BLOCKER 级校验项。
 */
public final class DomainPackageManifestParser {

    private final ObjectMapper objectMapper;

    public DomainPackageManifestParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 manifest JSON 字符串。
     *
     * @throws BusinessRuleException JSON 格式错误或字段缺失
     */
    public DomainPackageManifest parse(String manifestJson) {
        if (manifestJson == null || manifestJson.isBlank()) {
            throw new BusinessRuleException("manifestJson 不能为空");
        }
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            return parseManifest(root);
        } catch (BusinessRuleException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessRuleException("manifest 解析失败: " + e.getMessage());
        }
    }

    private DomainPackageManifest parseManifest(JsonNode root) {
        String schemaVersion = text(root, "schemaVersion", true);
        if (!"1.0".equals(schemaVersion)) {
            throw new BusinessRuleException("schemaVersion 必须为 1.0，实际为 " + schemaVersion);
        }
        String stableKey = text(root, "stableKey", true);
        String name = text(root, "name", true);
        String layerStr = text(root, "layer", true);
        DomainPackageLayer layer = parseEnum(layerStr, DomainPackageLayer.class);
        String semanticVersion = text(root, "version", true);

        // extends 对象（可选；PLATFORM_STANDARD 不应有 extends）
        String extendsParentKey = null;
        String extendsVersionRange = null;
        String parentSnapshotId = null;
        JsonNode extendsNode = root.get("extends");
        if (extendsNode != null && !extendsNode.isNull()) {
            extendsParentKey = text(extendsNode, "packageKey", true);
            extendsVersionRange = text(extendsNode, "versionRange", true);
            JsonNode snapshotNode = extendsNode.get("parentSnapshotId");
            if (snapshotNode != null && !snapshotNode.isNull()) {
                parentSnapshotId = snapshotNode.asText();
            }
        }

        List<DomainPackageCoreFieldReuse> coreFieldReuses =
                parseCoreFieldReuses(array(root, "coreFieldReuse"), semanticVersion);
        List<ObjectDefinition> objects = parseObjects(array(root, "objects"));
        List<PageDefinition> pages = parsePages(array(root, "pages"));
        List<ViewDefinition> views = parseViews(array(root, "views"));
        List<RuleDefinition> rules = parseRules(array(root, "rules"));
        List<DomainPackageWorkflowBinding> workflowBindings = parseWorkflowBindings(array(root, "workflowBindings"));
        List<PermissionDefinition> permissions = parsePermissions(array(root, "permissions"));
        List<ExtensionDefinition> extensions = parseExtensions(array(root, "extensions"));
        List<OverrideDefinition> overrides = parseOverrides(array(root, "overrides"));

        return new DomainPackageManifest(
                schemaVersion, stableKey, name, layer.name(), semanticVersion,
                extendsParentKey, extendsVersionRange, parentSnapshotId,
                coreFieldReuses, objects, pages, views, rules,
                workflowBindings, permissions, extensions, overrides);
    }

    // ============================================================
    // 核心字段复用声明
    // ============================================================

    private List<DomainPackageCoreFieldReuse> parseCoreFieldReuses(ArrayNode node, String ignored) {
        List<DomainPackageCoreFieldReuse> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            // id 与 versionId 由应用层在保存时分配；解析阶段使用占位 UUID。
            UUID id = UUID.randomUUID();
            UUID versionId = UUID.randomUUID(); // 占位；保存时由应用层覆盖
            String coreFieldKey = text(item, "coreFieldKey", true);
            String coreObjectType = text(item, "coreObjectType", true);
            String dispositionStr = text(item, "disposition", true);
            CoreFieldReuseDisposition disposition = parseEnum(dispositionStr, CoreFieldReuseDisposition.class);
            String reason = optionalText(item, "reason");
            String extensionFieldKey = optionalText(item, "extensionFieldKey");
            result.add(new DomainPackageCoreFieldReuse(
                    id, versionId, coreFieldKey, coreObjectType,
                    disposition, reason, extensionFieldKey, Instant.now()));
        }
        return result;
    }

    // ============================================================
    // 对象定义
    // ============================================================

    private List<ObjectDefinition> parseObjects(ArrayNode node) {
        List<ObjectDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            result.add(parseObject(item));
        }
        return result;
    }

    private ObjectDefinition parseObject(JsonNode item) {
        String stableKey = text(item, "stableKey", true);
        String kindStr = text(item, "kind", true);
        ObjectKind kind = parseEnum(kindStr, ObjectKind.class);
        String coreObjectType = optionalText(item, "coreObjectType");
        LocalizedText label = parseLocalizedText(item.get("label"));
        String titleFieldKey = optionalText(item, "titleFieldKey");
        List<FieldDefinition> fields = parseFields(array(item, "fields"));
        List<RelationDefinition> relations = parseRelations(array(item, "relations"));
        return new ObjectDefinition(stableKey, kind, coreObjectType, label,
                titleFieldKey, fields, relations);
    }

    private List<FieldDefinition> parseFields(ArrayNode node) {
        List<FieldDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            result.add(parseField(item));
        }
        return result;
    }

    private FieldDefinition parseField(JsonNode item) {
        String stableKey = text(item, "stableKey", true);
        LocalizedText label = parseLocalizedText(item.get("label"));
        DataType dataType = parseEnum(text(item, "dataType", true), DataType.class);
        boolean required = bool(item, "required", false);
        boolean sensitive = bool(item, "sensitive", false);
        UniqueScope uniqueScope = parseEnum(optionalText(item, "uniqueScope"), UniqueScope.class, UniqueScope.NONE);
        IndexMode indexMode = parseEnum(optionalText(item, "indexMode"), IndexMode.class, IndexMode.NONE);
        String coreFieldKey = optionalText(item, "coreFieldKey");
        Object defaultValue = item.has("defaultValue") ? objectMapper.convertValue(item.get("defaultValue"), Object.class) : null;
        Map<String, Object> validation = parseStringObjectMap(item.get("validation"));
        List<FieldDefinition.FieldOption> options = parseFieldOptions(array(item, "options"));
        return new FieldDefinition(stableKey, label, dataType, required, sensitive,
                uniqueScope, indexMode, coreFieldKey, defaultValue, validation, options);
    }

    private List<FieldDefinition.FieldOption> parseFieldOptions(ArrayNode node) {
        List<FieldDefinition.FieldOption> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String key = text(item, "key", true);
            LocalizedText label = parseLocalizedText(item.get("label"));
            result.add(new FieldDefinition.FieldOption(key, label));
        }
        return result;
    }

    private List<RelationDefinition> parseRelations(ArrayNode node) {
        List<RelationDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String stableKey = text(item, "stableKey", true);
            String targetObjectKey = text(item, "targetObjectKey", true);
            Cardinality cardinality = parseEnum(text(item, "cardinality", true), Cardinality.class);
            boolean required = bool(item, "required", false);
            RelationOwnership ownership = parseEnum(optionalText(item, "ownership"),
                    RelationOwnership.class, RelationOwnership.REFERENCE);
            result.add(new RelationDefinition(stableKey, targetObjectKey, cardinality, required, ownership));
        }
        return result;
    }

    // ============================================================
    // 页面与视图
    // ============================================================

    private List<PageDefinition> parsePages(ArrayNode node) {
        List<PageDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String stableKey = text(item, "stableKey", true);
            String objectKey = text(item, "objectKey", true);
            Map<String, Object> layout = parseStringObjectMap(item.get("layout"));
            String visibilityRuleKey = optionalText(item, "visibilityRuleKey");
            result.add(new PageDefinition(stableKey, objectKey, layout, visibilityRuleKey));
        }
        return result;
    }

    private List<ViewDefinition> parseViews(ArrayNode node) {
        List<ViewDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String stableKey = text(item, "stableKey", true);
            String objectKey = text(item, "objectKey", true);
            ViewType viewType = parseEnum(text(item, "viewType", true), ViewType.class);
            List<String> columns = parseStringList(array(item, "columns"));
            List<Map<String, Object>> filters = parseMapList(array(item, "filters"));
            result.add(new ViewDefinition(stableKey, objectKey, viewType, columns, filters));
        }
        return result;
    }

    // ============================================================
    // 规则定义
    // ============================================================

    private List<RuleDefinition> parseRules(ArrayNode node) {
        List<RuleDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String stableKey = text(item, "stableKey", true);
            String event = text(item, "event", true);
            Map<String, Object> condition = parseStringObjectMap(item.get("condition"));
            List<RuleAction> actions = parseRuleActions(array(item, "actions"));
            ExecutionIdentity executionIdentity = parseEnum(text(item, "executionIdentity", true),
                    ExecutionIdentity.class);
            RuleMode mode = parseEnum(optionalText(item, "mode"), RuleMode.class, RuleMode.ASYNCHRONOUS);
            String cycleDetectionKey = optionalText(item, "cycleDetectionKey");
            result.add(new RuleDefinition(stableKey, event, condition, actions,
                    executionIdentity, mode, cycleDetectionKey));
        }
        return result;
    }

    private List<RuleAction> parseRuleActions(ArrayNode node) {
        List<RuleAction> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            RuleActionType type = parseEnum(text(item, "type", true), RuleActionType.class);
            Map<String, Object> parameters = parseStringObjectMap(item.get("parameters"));
            result.add(new RuleAction(type, parameters));
        }
        return result;
    }

    // ============================================================
    // 工作流绑定
    // ============================================================

    private List<DomainPackageWorkflowBinding> parseWorkflowBindings(ArrayNode node) {
        List<DomainPackageWorkflowBinding> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            // 解析阶段 id/packageId/versionId 为占位；保存时由应用层覆盖
            UUID id = UUID.randomUUID();
            UUID packageId = UUID.randomUUID();
            UUID versionId = null;
            JsonNode versionNode = item.get("versionId");
            if (versionNode != null && !versionNode.isNull()) {
                versionId = UUID.fromString(versionNode.asText());
            }
            String stableKey = text(item, "stableKey", true);
            String processDefinitionKey = text(item, "processDefinitionKey", true);
            String businessVersion = text(item, "businessVersion", true);
            String bpmnResource = text(item, "bpmnResource", true);
            WorkflowBindingTrigger trigger = parseEnum(text(item, "trigger", true),
                    WorkflowBindingTrigger.class);
            String objectTypeKey = optionalText(item, "objectTypeKey");
            String eventType = optionalText(item, "eventType");
            String authorizationPolicyKey = text(item, "authorizationPolicyKey", true);
            Map<String, String> variableMappings = parseStringStringMap(item.get("variableMappings"));
            String startupConditionRuleKey = optionalText(item, "startupConditionRuleKey");
            List<String> declaredPermissions = parseStringList(array(item, "declaredPermissions"));
            InstanceMigrationPolicy instanceMigrationPolicy = parseEnum(
                    optionalText(item, "instanceMigrationPolicy"),
                    InstanceMigrationPolicy.class, InstanceMigrationPolicy.PINNED);
            String migrationValidationRuleKey = optionalText(item, "migrationValidationRuleKey");
            Instant now = Instant.now();
            result.add(new DomainPackageWorkflowBinding(
                    id, packageId, versionId, stableKey,
                    processDefinitionKey, businessVersion, bpmnResource, trigger,
                    objectTypeKey, eventType, authorizationPolicyKey,
                    variableMappings, startupConditionRuleKey, declaredPermissions,
                    instanceMigrationPolicy, migrationValidationRuleKey,
                    1, now, now));
        }
        return result;
    }

    // ============================================================
    // 权限定义
    // ============================================================

    private List<PermissionDefinition> parsePermissions(ArrayNode node) {
        List<PermissionDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String capabilityKey = text(item, "capabilityKey", true);
            String objectKey = text(item, "objectKey", true);
            List<String> operations = parseStringList(array(item, "operations"));
            List<String> fieldKeys = parseStringList(array(item, "fieldKeys"));
            Map<String, Object> dataScope = parseStringObjectMap(item.get("dataScope"));
            result.add(new PermissionDefinition(capabilityKey, objectKey, operations, fieldKeys, dataScope));
        }
        return result;
    }

    // ============================================================
    // 扩展定义
    // ============================================================

    private List<ExtensionDefinition> parseExtensions(ArrayNode node) {
        List<ExtensionDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String stableKey = text(item, "stableKey", true);
            String artifact = text(item, "artifact", true);
            String entrypoint = text(item, "entrypoint", true);
            List<String> permissions = parseStringList(array(item, "permissions"));
            int timeoutMs = item.has("timeoutMs") ? item.get("timeoutMs").asInt() : 5000;
            int memoryMb = item.has("memoryMb") ? item.get("memoryMb").asInt() : 0;
            String signature = optionalText(item, "signature");
            ExtensionIsolationPolicy isolationPolicy = parseEnum(
                    optionalText(item, "isolationPolicy"),
                    ExtensionIsolationPolicy.class, ExtensionIsolationPolicy.STRICT_SANDBOX);
            ExtensionFailurePolicy failurePolicy = parseEnum(
                    optionalText(item, "failurePolicy"),
                    ExtensionFailurePolicy.class, ExtensionFailurePolicy.FAIL_FAST);
            result.add(new ExtensionDefinition(stableKey, artifact, entrypoint, permissions,
                    timeoutMs, memoryMb, signature, isolationPolicy, failurePolicy));
        }
        return result;
    }

    // ============================================================
    // 覆盖定义
    // ============================================================

    private List<OverrideDefinition> parseOverrides(ArrayNode node) {
        List<OverrideDefinition> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            String targetStableKey = text(item, "targetStableKey", true);
            String propertyPath = text(item, "propertyPath", true);
            Object oldValue = item.has("oldValue")
                    ? objectMapper.convertValue(item.get("oldValue"), Object.class) : null;
            Object newValue = item.has("newValue")
                    ? objectMapper.convertValue(item.get("newValue"), Object.class) : null;
            String reason = text(item, "reason", true);
            JsonNode approverNode = item.get("approvedBy");
            PrincipalRef approvedBy = approverNode == null || approverNode.isNull()
                    ? null : parsePrincipalRef(approverNode);
            result.add(new OverrideDefinition(targetStableKey, propertyPath, oldValue, newValue,
                    reason, approvedBy));
        }
        return result;
    }

    private PrincipalRef parsePrincipalRef(JsonNode node) {
        PrincipalType type = parseEnum(text(node, "principalType", true), PrincipalType.class);
        String id = text(node, "principalId", true);
        String label = optionalText(node, "displayLabel");
        return new PrincipalRef(type, id, label);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private LocalizedText parseLocalizedText(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new BusinessRuleException("LocalizedText 不能为 null");
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                values.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return new LocalizedText(values);
    }

    private Map<String, Object> parseStringObjectMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        Map<String, Object> result = new HashMap<>();
        node.fields().forEachRemaining(entry ->
                result.put(entry.getKey(),
                        objectMapper.convertValue(entry.getValue(), Object.class)));
        return result;
    }

    private Map<String, String> parseStringStringMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && !entry.getValue().isNull()) {
                result.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return result;
    }

    private List<String> parseStringList(ArrayNode node) {
        List<String> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            if (item != null && !item.isNull()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private List<Map<String, Object>> parseMapList(ArrayNode node) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        for (JsonNode item : node) {
            result.add(parseStringObjectMap(item));
        }
        return result;
    }

    private String text(JsonNode node, String field, boolean required) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            if (required) {
                throw new BusinessRuleException("字段 " + field + " 不能为空");
            }
            return null;
        }
        return child.asText();
    }

    private String optionalText(JsonNode node, String field) {
        return text(node, field, false);
    }

    private boolean bool(JsonNode node, String field, boolean defaultValue) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return defaultValue;
        }
        return child.asBoolean(defaultValue);
    }

    private ArrayNode array(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isArray()) {
            throw new BusinessRuleException("字段 " + field + " 必须为数组");
        }
        return (ArrayNode) child;
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (value == null) {
            throw new BusinessRuleException("枚举 " + enumClass.getSimpleName() + " 不能为空");
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("无效的 " + enumClass.getSimpleName() + ": " + value);
        }
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass, E defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}
