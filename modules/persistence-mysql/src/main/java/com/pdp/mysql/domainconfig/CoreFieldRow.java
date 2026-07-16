package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.metamodel.CoreFieldDefinition;
import com.pdp.persistence.type.JsonDocument;
import java.util.UUID;

public record CoreFieldRow(
    UUID id,
    String stableKey,
    String objectType,
    String semanticName,
    String dataType,
    String dataSource,
    JsonDocument aliases,
    boolean extensible) {

  CoreFieldDefinition toDomain() {
    return new CoreFieldDefinition(
        stableKey,
        objectType,
        semanticName,
        dataType,
        dataSource,
        DomainConfigJsonCodec.stringSet(aliases),
        extensible);
  }
}
