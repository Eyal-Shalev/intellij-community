// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.storage.api;

import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.List;

// 128 bit key
public class Address {
  private final long highBytes;
  private final long lowBytes;

  public Address(long lowBytes) {
    if (lowBytes < 0) {
      throw new IllegalArgumentException("novelty address expected");
    }
    this.highBytes = 0;
    this.lowBytes = lowBytes;
  }

  public Address(long highBytes, long lowBytes) {
    if (lowBytes >= 0 && highBytes != 0) {
      throw new IllegalArgumentException("non-novelty address expected");
    }
    this.highBytes = highBytes;
    this.lowBytes = lowBytes;
  }

  public long getHighBytes() {
    return highBytes;
  }

  public long getLowBytes() {
    return lowBytes;
  }

  public boolean isNovelty() {
    return lowBytes >= 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Address address = (Address)o;
    return highBytes == address.highBytes && lowBytes == address.lowBytes;
  }

  @Override
  public int hashCode() {
    int result = 1;

    result = 31 * result + Long.hashCode(highBytes);
    result = 31 * result + Long.hashCode(lowBytes);

    return result;
  }

  @Override
  public String toString() {
    byte[] bytes = new byte[16];
    long processed = highBytes;
    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte)(processed & 0xff);
      processed >>= 8;
    }
    processed = lowBytes;
    for (int i = 8; i < 16; i++) {
      bytes[i] = (byte)(processed & 0xff);
      processed >>= 8;
    }
    return Base64.getEncoder().encodeToString(bytes);
  }

  public static Address fromStrings(@NotNull final List<String> list) {
    return new Address(Long.parseLong(list.get(1)), Long.parseLong(list.get(0)));
  }
}
