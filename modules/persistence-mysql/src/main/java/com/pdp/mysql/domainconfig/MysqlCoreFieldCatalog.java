package com.pdp.mysql.domainconfig;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalog;
import com.pdp.domainconfig.port.CoreFieldCatalogPort;
import org.springframework.stereotype.Repository;

@Repository
public final class MysqlCoreFieldCatalog implements CoreFieldCatalogPort {

  private final CoreFieldCatalogMapper mapper;

  public MysqlCoreFieldCatalog(CoreFieldCatalogMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public CoreFieldCatalog load() {
    return new CoreFieldCatalog(mapper.findAll().stream().map(CoreFieldRow::toDomain).toList());
  }
}
