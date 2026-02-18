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
import java.util.List;

import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;

final class BatchMarshaler extends MarshalerWithSize {

  private final SpanMarshaler[] spans;
  private final ProcessMarshaler process;

  static BatchMarshaler create(List<SpanData> spans, Resource resource) {
    SpanMarshaler[] spanMarshalers = SpanMarshaler.createRepeated(spans);
    ProcessMarshaler processMarshaler = ProcessMarshaler.create(resource);
    return new BatchMarshaler(spanMarshalers, processMarshaler);
  }

  BatchMarshaler(SpanMarshaler[] spans, ProcessMarshaler process) {
    super(calculateSize(spans, process));
    this.spans = spans;
    this.process = process;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeRepeatedMessage(Batch.SPANS, spans);
    output.serializeMessage(Batch.PROCESS, process);
  }

  private static int calculateSize(SpanMarshaler[] spans, ProcessMarshaler process) {
    int size = 0;
    size += MarshalerUtil.sizeRepeatedMessage(Batch.SPANS, spans);
    size += MarshalerUtil.sizeMessage(Batch.PROCESS, process);
    return size;
  }
}
