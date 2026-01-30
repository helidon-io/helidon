/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

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
