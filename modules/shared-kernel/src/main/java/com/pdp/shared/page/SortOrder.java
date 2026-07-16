package com.pdp.shared.page;

import java.util.Objects;

public record SortOrder(String field, SortDirection direction, boolean unique) {
  public SortOrder {
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("排序字段不能为空");
    }
    Objects.requireNonNull(direction, "排序方向不能为空");
  }
}
