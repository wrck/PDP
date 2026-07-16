package com.pdp.shared.page;

import java.time.Instant;
import java.util.List;

public record KeysetCursor(
    String workspaceKey,
    String filterDigest,
    List<SortOrder> sortOrders,
    List<String> values,
    Instant issuedAt) {
  public KeysetCursor {
    if (workspaceKey == null || workspaceKey.isBlank()) {
      throw new IllegalArgumentException("工作空间键不能为空");
    }
    if (filterDigest == null || filterDigest.isBlank()) {
      throw new IllegalArgumentException("筛选摘要不能为空");
    }
    sortOrders = List.copyOf(sortOrders);
    values = List.copyOf(values);
    if (sortOrders.isEmpty() || !sortOrders.getLast().unique()) {
      throw new IllegalArgumentException("稳定排序必须以唯一字段收尾");
    }
    if (sortOrders.size() != values.size()) {
      throw new IllegalArgumentException("排序字段与游标值数量必须一致");
    }
    if (issuedAt == null) {
      throw new IllegalArgumentException("签发时间不能为空");
    }
  }
}
