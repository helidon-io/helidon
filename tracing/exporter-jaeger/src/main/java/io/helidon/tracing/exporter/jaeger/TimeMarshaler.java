/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.exporter.jaeger;

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
