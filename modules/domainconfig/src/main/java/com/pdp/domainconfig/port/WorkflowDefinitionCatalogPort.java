package com.pdp.domainconfig.port;

import com.pdp.domainconfig.domain.behavior.WorkflowBinding;
import java.util.List;

@FunctionalInterface
public interface WorkflowDefinitionCatalogPort {
  List<String> validate(WorkflowBinding binding);
}
