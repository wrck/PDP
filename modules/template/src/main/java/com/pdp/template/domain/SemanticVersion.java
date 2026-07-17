package com.pdp.template.domain;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** PDP P1 项目模板使用的三段式语义版本。 */
public record SemanticVersion(String value) implements Comparable<SemanticVersion> {
  private static final Pattern PATTERN = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");

  public SemanticVersion {
    if (value == null || !PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("项目模板版本必须使用三段式语义版本");
    }
  }

  @Override
  public int compareTo(SemanticVersion other) {
    String[] left = value.split("\\.");
    String[] right = other.value.split("\\.");
    for (int index = 0; index < left.length; index++) {
      int compared = new BigInteger(left[index]).compareTo(new BigInteger(right[index]));
      if (compared != 0) {
        return compared;
      }
    }
    return 0;
  }

  @Override
  public String toString() {
    return value;
  }
}
