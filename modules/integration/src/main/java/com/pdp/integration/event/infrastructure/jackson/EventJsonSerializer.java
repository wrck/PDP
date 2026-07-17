package com.pdp.integration.event.infrastructure.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pdp.integration.event.domain.DomainEvent;
import org.springframework.stereotype.Component;

/**
 * 事件 JSON 序列化器。
 *
 * <p>使用 Jackson {@link ObjectMapper} 将 {@link DomainEvent} 序列化为 {@link JsonNode}
 * 存入 Outbox 的 {@code payload} 列（MySQL JSON 类型）。
 *
 * <p>配置：
 * <ul>
 *   <li>注册 {@link JavaTimeModule} 以支持 Instant 等时间类型</li>
 *   <li>禁用 WRITE_DATES_AS_TIMESTAMPS（ISO-8601 字符串存储）</li>
 *   <li>禁用 FAIL_ON_UNKNOWN_PROPERTIES（向前兼容 schema 演进）</li>
 * </ul>
 *
 * <p>线程安全：ObjectMapper 配置完成后是线程安全的，可作为单例共享。
 */
@Component
public class EventJsonSerializer {

    private final ObjectMapper objectMapper;

    public EventJsonSerializer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** 将 DomainEvent 序列化为 JsonNode（用于存入 Outbox payload）。 */
    public JsonNode serialize(DomainEvent event) {
        return objectMapper.valueToTree(event);
    }

    /** 将 payload JsonNode 反序列化为指定事件类型（监听器消费时使用）。 */
    public <T extends DomainEvent> T deserialize(JsonNode payload, Class<T> eventType) {
        try {
            return objectMapper.treeToValue(payload, eventType);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "事件 payload 反序列化失败: " + eventType.getName() + " - " + e.getMessage(), e);
        }
    }

    /** 暴露内部 ObjectMapper（仅供框架集成或测试使用，业务代码不应直接调用）。 */
    ObjectMapper objectMapper() {
        return objectMapper;
    }
}
