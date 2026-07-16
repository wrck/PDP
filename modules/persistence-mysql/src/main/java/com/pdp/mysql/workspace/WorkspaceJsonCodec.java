package com.pdp.mysql.workspace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdp.persistence.type.JsonDocument;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class WorkspaceJsonCodec {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private WorkspaceJsonCodec() {}

  static JsonDocument stringSet(Set<String> values) {
    return document(new TreeSet<>(values));
  }

  static Set<String> stringSet(JsonDocument document) {
    return Set.copyOf(
        read(document, new TypeReference<LinkedHashSet<String>>() {}));
  }

  static JsonDocument uuidSet(Set<UUID> values) {
    return document(values.stream().map(UUID::toString).sorted().toList());
  }

  static Set<UUID> uuidSet(JsonDocument document) {
    LinkedHashSet<UUID> values = new LinkedHashSet<>();
    for (String value : read(document, new TypeReference<java.util.List<String>>() {})) {
      values.add(UUID.fromString(value));
    }
    return Set.copyOf(values);
  }

  static JsonDocument map(Map<String, Object> value) {
    return document(value);
  }

  static Map<String, Object> map(JsonDocument document) {
    return Map.copyOf(read(document, new TypeReference<Map<String, Object>>() {}));
  }

  private static JsonDocument document(Object value) {
    try {
      return new JsonDocument(MAPPER.writeValueAsString(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("工作空间持久化 JSON 序列化失败", exception);
    }
  }

  private static <T> T read(JsonDocument document, TypeReference<T> type) {
    try {
      return MAPPER.readValue(document.value(), type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("工作空间持久化 JSON 反序列化失败", exception);
    }
  }
}
