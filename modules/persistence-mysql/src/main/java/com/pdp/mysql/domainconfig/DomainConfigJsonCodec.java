package com.pdp.mysql.domainconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pdp.persistence.type.JsonDocument;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

final class DomainConfigJsonCodec {

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .findAndRegisterModules()
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private DomainConfigJsonCodec() {}

  static JsonDocument write(Object value) {
    try {
      return new JsonDocument(MAPPER.writeValueAsString(value));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("领域包持久化 JSON 序列化失败", exception);
    }
  }

  static <T> T read(JsonDocument document, Class<T> type) {
    try {
      return MAPPER.readValue(document.value(), type);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("领域包持久化 JSON 反序列化失败", exception);
    }
  }

  static Set<String> stringSet(JsonDocument document) {
    try {
      return Set.copyOf(
          MAPPER.readValue(document.value(), new TypeReference<List<String>>() {}));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("核心字段别名 JSON 反序列化失败", exception);
    }
  }

  static JsonDocument stringSet(Set<String> values) {
    return write(new TreeSet<>(values));
  }

  static JsonDocument uuidList(List<UUID> values) {
    return write(values.stream().map(UUID::toString).toList());
  }
}
