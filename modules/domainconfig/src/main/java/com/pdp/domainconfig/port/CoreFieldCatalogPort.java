package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.metamodel.CoreFieldCatalog;

@FunctionalInterface
public interface CoreFieldCatalogPort {
  CoreFieldCatalog load();
}
