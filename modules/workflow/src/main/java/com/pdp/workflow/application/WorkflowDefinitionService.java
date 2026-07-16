package com.pdp.workflow.application;

import com.pdp.workflow.domain.WorkflowDefinition;
import com.pdp.workflow.domain.WorkflowDeployment;
import com.pdp.workflow.port.WorkflowDefinitionPort;
import com.pdp.workflow.port.WorkflowRepository;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

public final class WorkflowDefinitionService implements WorkflowDefinitionPort {
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9.-]{2,99}$");
    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+$");
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private final WorkflowRepository repository;
    private final Clock clock;
    private final Supplier<UUID> ids;

    public WorkflowDefinitionService(WorkflowRepository repository, Clock clock, Supplier<UUID> ids) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
        this.ids = Objects.requireNonNull(ids);
    }

    @Override
    public ValidationResult validate(ValidateCommand command) {
        List<Finding> findings = new ArrayList<>();
        if (command == null || !KEY.matcher(nullToEmpty(command.processDefinitionKey())).matches()) {
            findings.add(new Finding(Severity.ERROR, "WORKFLOW_KEY_INVALID", "流程键格式无效"));
        }
        if (command == null || !VERSION.matcher(nullToEmpty(command.businessVersion())).matches()) {
            findings.add(new Finding(Severity.ERROR, "BUSINESS_VERSION_INVALID", "业务版本必须为语义化版本"));
        }
        String xml = command == null ? "" : nullToEmpty(command.bpmnXml());
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var root = document.getDocumentElement();
            if (!"definitions".equals(root.getLocalName()) || !BPMN_NS.equals(root.getNamespaceURI())) {
                findings.add(new Finding(Severity.ERROR, "BPMN_NAMESPACE_INVALID", "必须使用 BPMN 2.0.2 definitions"));
            }
            if (document.getElementsByTagNameNS(BPMN_NS, "process").getLength() == 0) {
                findings.add(new Finding(Severity.ERROR, "BPMN_PROCESS_MISSING", "流程定义缺少 process"));
            }
        } catch (Exception ex) {
            findings.add(new Finding(Severity.ERROR, "BPMN_XML_INVALID", "BPMN XML 无法安全解析"));
        }
        String hash = sha256(normalize(xml));
        return new ValidationResult(findings.stream().noneMatch(f -> f.severity() == Severity.ERROR), hash, findings);
    }

    @Override
    public WorkflowDefinition deploy(DeployCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("部署幂等键不能为空");
        }
        Optional<WorkflowDeployment> repeated =
                repository.findDeploymentByIdempotencyKey(command.idempotencyKey());
        if (repeated.isPresent()) {
            return repository.findDefinition(repeated.get().definitionId())
                    .orElseThrow(() -> new IllegalStateException("部署记录引用的定义不存在"));
        }
        ValidationResult result = validate(new ValidateCommand(command.processDefinitionKey(),
                command.businessVersion(), command.domainPackageVersionId(), command.bpmnXml()));
        if (!result.valid() || !result.contentHash().equalsIgnoreCase(command.contentHash())) {
            throw new IllegalArgumentException("流程定义校验失败或内容哈希不匹配");
        }
        Optional<WorkflowDefinition> existing =
                repository.findDefinition(command.processDefinitionKey(), command.businessVersion());
        if (existing.isPresent()) {
            if (!existing.get().contentHash().equals(result.contentHash())) {
                throw new IllegalStateException("相同流程键与业务版本不能部署不同内容");
            }
            return existing.get();
        }
        WorkflowDefinition validated = new WorkflowDefinition(ids.get(), command.processDefinitionKey(),
                command.businessVersion(), result.contentHash(), command.domainPackageVersionId(),
                WorkflowDefinition.Status.VALIDATED, null);
        WorkflowDefinition deployed = repository.saveDefinition(validated.deploy(clock.instant()));
        repository.saveDeployment(new WorkflowDeployment(ids.get(), deployed.id(),
                "pending:" + deployed.id(), command.idempotencyKey(), clock.instant()));
        return deployed;
    }

    @Override
    public Optional<WorkflowDefinition> find(UUID definitionId) {
        return repository.findDefinition(definitionId);
    }

    private static String normalize(String xml) {
        return xml.replace("\r\n", "\n").strip();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
