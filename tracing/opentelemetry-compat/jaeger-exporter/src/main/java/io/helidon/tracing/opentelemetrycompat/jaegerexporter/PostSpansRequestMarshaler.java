/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

import java.io.IOException;
import java.util.List;

import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.ProtoFieldInfo;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;

final class PostSpansRequestMarshaler extends MarshalerWithSize {

  private static final ProtoFieldInfo BATCH = ProtoFieldInfo.create(1, 10, "batch");

  private final BatchMarshaler batch;

  static PostSpansRequestMarshaler create(List<SpanData> spans, Resource resource) {
    return new PostSpansRequestMarshaler(BatchMarshaler.create(spans, resource));
  }

  PostSpansRequestMarshaler(BatchMarshaler batch) {
    super(calculateSize(batch));
    this.batch = batch;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeMessage(BATCH, batch);
  }

  private static int calculateSize(BatchMarshaler batch) {
    int size = 0;
    size += MarshalerUtil.sizeMessage(BATCH, batch);
    return size;
  }
}
