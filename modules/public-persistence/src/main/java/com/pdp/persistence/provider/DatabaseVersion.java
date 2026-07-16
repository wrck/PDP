package com.pdp.persistence.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用于认证门槛比较的数据库版本，不向领域层暴露驱动版本类型。
 */
public record DatabaseVersion(String value) implements Comparable<DatabaseVersion> {

    public DatabaseVersion {
        value = Objects.requireNonNull(value, "value").trim();
        if (value.isEmpty() || !Character.isDigit(value.charAt(0))) {
            throw new IllegalArgumentException("数据库版本必须以数字开头");
        }
    }

    @Override
    public int compareTo(DatabaseVersion other) {
        var left = numericSegments(value);
        var right = numericSegments(other.value);
        for (int index = 0; index < Math.max(left.size(), right.size()); index++) {
            int leftValue = index < left.size() ? left.get(index) : 0;
            int rightValue = index < right.size() ? right.get(index) : 0;
            int comparison = Integer.compare(leftValue, rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static List<Integer> numericSegments(String value) {
        List<Integer> segments = new ArrayList<>();
        for (String segment : value.split("[^0-9]+")) {
            if (!segment.isEmpty()) {
                segments.add(Integer.parseInt(segment));
            }
        }
        return segments;
    }
}
