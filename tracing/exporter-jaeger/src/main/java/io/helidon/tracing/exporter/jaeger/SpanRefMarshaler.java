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
import java.util.ArrayList;
import java.util.List;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.ProtoEnumInfo;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.sdk.trace.data.LinkData;

final class SpanRefMarshaler extends MarshalerWithSize {

  private final String traceId;
  private final String spanId;
  private final ProtoEnumInfo refType;

  static List<SpanRefMarshaler> createRepeated(List<LinkData> links) {
    List<SpanRefMarshaler> marshalers = new ArrayList<>(links.size());
    for (LinkData link : links) {
      // we can assume that all links are *follows from*
      // https://github.com/open-telemetry/opentelemetry-java/issues/475
      // https://github.com/open-telemetry/opentelemetry-java/pull/481/files#r312577862
      marshalers.add(create(link));
    }
    return marshalers;
  }

  static SpanRefMarshaler create(SpanContext spanContext) {
    return new SpanRefMarshaler(
            spanContext.getTraceId(), spanContext.getSpanId(), SpanRefType.CHILD_OF);
  }

  static SpanRefMarshaler create(LinkData link) {
    return new SpanRefMarshaler(
        link.getSpanContext().getTraceId(),
        link.getSpanContext().getSpanId(),
        SpanRefType.FOLLOWS_FROM);
  }

  SpanRefMarshaler(String traceId, String spanId, ProtoEnumInfo refType) {
    super(calculateSize(traceId, spanId, refType));
    this.traceId = traceId;
    this.spanId = spanId;
    this.refType = refType;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeTraceId(SpanRef.TRACE_ID, traceId);
    output.serializeSpanId(SpanRef.SPAN_ID, spanId);
    output.serializeEnum(SpanRef.REF_TYPE, refType);
  }

  private static int calculateSize(String traceId, String spanId, ProtoEnumInfo refType) {
    int size = 0;
    size += MarshalerUtil.sizeTraceId(SpanRef.TRACE_ID, traceId);
    size += MarshalerUtil.sizeSpanId(SpanRef.SPAN_ID, spanId);
    size += MarshalerUtil.sizeEnum(SpanRef.REF_TYPE, refType);
    return size;
  }
}
