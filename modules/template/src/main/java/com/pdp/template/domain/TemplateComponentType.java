package com.pdp.template.domain;

import java.util.Set;

/** 模板定义中可治理的默认计划组件类型。 */
public enum TemplateComponentType {
  STAGE,
  TASK,
  OWNER_RULE,
  DURATION_RULE,
  MILESTONE,
  CHECKLIST_ITEM,
  DELIVERABLE,
  APPROVAL,
  VIEW;

  public boolean acceptsParent(TemplateComponentType parentType) {
    return switch (this) {
      case STAGE -> Set.of(STAGE).contains(parentType);
      case TASK -> Set.of(STAGE, TASK).contains(parentType);
      case MILESTONE -> parentType == STAGE;
      case CHECKLIST_ITEM -> parentType == TASK;
      case DELIVERABLE -> Set.of(STAGE, TASK).contains(parentType);
      case APPROVAL -> Set.of(STAGE, TASK, DELIVERABLE).contains(parentType);
      case OWNER_RULE, DURATION_RULE, VIEW -> false;
    };
  }

  public boolean producesProjectObject() {
    return this != OWNER_RULE && this != DURATION_RULE;
  }
}
