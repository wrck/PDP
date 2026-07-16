package com.pdp.shared.concurrency;

public record Revision(long value) {
  public Revision {
    if (value < 0) {
      throw new IllegalArgumentException("revision 不能小于 0");
    }
  }

  public Revision next() {
    return new Revision(Math.addExact(value, 1));
  }
}
