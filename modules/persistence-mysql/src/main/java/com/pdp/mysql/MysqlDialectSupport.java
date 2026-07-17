package com.pdp.mysql;

import java.util.List;

/**
 * MySQL SQL 方言辅助。
 *
 * <p>生成 MySQL 8.4 专用 SQL 片段：keyset 谓词（NULL 排序、复合 keyset WHERE）、LIMIT 语法。
 * 所有可分页查询必须定义稳定排序，最终排序键为 id（UUIDv7）。
 */
public class MysqlDialectSupport {

    /** MySQL LIMIT 子句。 */
    public String limitClause(int limit) {
        return "LIMIT " + limit;
    }

    /**
     * 生成 keyset 谓词片段（不含 WHERE 关键字），用于游标分页"下一页"。
     *
     * <p>排序字段列表按顺序参与比较，最终以 id 兜底保证稳定。ASC 时使用 {@code col > :last}，
     * NULL 视为最小值。生成的谓词使用命名占位符，调用方按列序绑定参数。
     *
     * @param sortColumns 排序列名列表（不含 id）
     * @param lastValues  上一页最后一行对应列值（字符串形式）
     * @param nullMarkers 每列是否为 NULL
     * @param direction   排序方向
     * @return SQL 谓词片段，例如 {@code (col1 > :v0 OR (col1 = :v0 AND col2 > :v1 OR ...)) OR ...}
     */
    public String keysetPredicate(List<String> sortColumns, List<String> lastValues,
                                  List<Boolean> nullMarkers, com.pdp.shared.page.SortDirection direction) {
        if (sortColumns == null || sortColumns.isEmpty()) {
            // 仅 id 兜底
            return "id " + op(direction) + " :lastId";
        }
        StringBuilder sb = new StringBuilder();
        // 简化实现：按"前 N-1 列相等，第 N 列大于"的级联 OR
        // 完整实现需处理 NULL 排序，此处给出基础结构供适配器扩展
        sb.append("(");
        for (int i = 0; i < sortColumns.size(); i++) {
            if (i > 0) {
                sb.append(" OR (");
                for (int j = 0; j < i; j++) {
                    if (j > 0) {
                        sb.append(" AND ");
                    }
                    sb.append(sortColumns.get(j)).append(" = :v").append(j);
                }
                sb.append(" AND ");
            } else if (i == 0 && sortColumns.size() > 1) {
                // 多列时第一项不带前缀括号
            }
            String col = sortColumns.get(i);
            boolean isNull = Boolean.TRUE.equals(nullMarkers.get(i));
            if (isNull) {
                // NULL 视为最小值，ASC 时任何非 NULL 都 > NULL
                sb.append("(").append(col).append(" IS NOT NULL");
                if (i < sortColumns.size() - 1) {
                    sb.append(" OR (").append(col).append(" IS NULL AND ");
                    // 递归由调用方补全
                }
                sb.append(")");
            } else {
                sb.append(col).append(" ").append(op(direction)).append(" :v").append(i);
            }
            if (i > 0) {
                sb.append(")");
            }
        }
        // id 兜底
        sb.append(" OR (");
        for (int j = 0; j < sortColumns.size(); j++) {
            if (j > 0) {
                sb.append(" AND ");
            }
            sb.append(sortColumns.get(j)).append(" = :v").append(j);
        }
        if (!sortColumns.isEmpty()) {
            sb.append(" AND ");
        }
        sb.append("id ").append(op(direction)).append(" :lastId");
        sb.append("))");
        return sb.toString();
    }

    private static String op(com.pdp.shared.page.SortDirection direction) {
        return direction == com.pdp.shared.page.SortDirection.ASC ? ">" : "<";
    }
}
