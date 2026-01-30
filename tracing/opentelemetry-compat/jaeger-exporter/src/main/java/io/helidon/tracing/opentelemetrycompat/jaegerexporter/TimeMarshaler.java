/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.Serializer;

// The wire format for Timestamp and Duration are exactly the same. Just implement one Marshaler
// for them.
final class TimeMarshaler extends MarshalerWithSize {
  private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

  private final long seconds;
  private final int nanos;

  static TimeMarshaler create(long timeNanos) {
    long seconds = timeNanos / NANOS_PER_SECOND;
    int nanos = (int) (timeNanos % NANOS_PER_SECOND);
    return new TimeMarshaler(seconds, nanos);
  }

  TimeMarshaler(long seconds, int nanos) {
    super(calculateSize(seconds, nanos));
    this.seconds = seconds;
    this.nanos = nanos;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeInt64(Time.SECONDS, seconds);
    output.serializeInt32(Time.NANOS, nanos);
  }

  private static int calculateSize(long seconds, int nanos) {
    int size = 0;
    size += MarshalerUtil.sizeInt64(Time.SECONDS, seconds);
    size += MarshalerUtil.sizeInt32(Time.NANOS, nanos);
    return size;
  }
}
