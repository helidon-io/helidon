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
import java.util.Locale;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.sdk.trace.data.SpanData;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;

final class SpanMarshaler extends MarshalerWithSize {

  private static final AttributeKey<Boolean> KEY_ERROR = booleanKey("error");
  private static final AttributeKey<Long> KEY_DROPPED_ATTRIBUTES_COUNT =
      AttributeKey.longKey("otel.dropped_attributes_count");
  private static final AttributeKey<Long> KEY_DROPPED_EVENTS_COUNT =
      AttributeKey.longKey("otel.dropped_events_count");
  private static final AttributeKey<String> KEY_SPAN_KIND = AttributeKey.stringKey("span.kind");
  private static final AttributeKey<String> KEY_SPAN_STATUS_MESSAGE =
      AttributeKey.stringKey("otel.status_description");
  private static final AttributeKey<String> KEY_SPAN_STATUS_CODE =
      AttributeKey.stringKey("otel.status_code");
  private static final AttributeKey<String> KEY_INSTRUMENTATION_SCOPE_NAME =
      AttributeKey.stringKey("otel.scope.name");
  private static final AttributeKey<String> KEY_INSTRUMENTATION_SCOPE_VERSION =
      AttributeKey.stringKey("otel.scope.version");
  private static final AttributeKey<String> KEY_INSTRUMENTATION_LIBRARY_NAME =
      AttributeKey.stringKey("otel.library.name");
  private static final AttributeKey<String> KEY_INSTRUMENTATION_LIBRARY_VERSION =
      AttributeKey.stringKey("otel.library.version");

  private final String traceId;
  private final String spanId;
  private final byte[] operationNameUtf8;
  private final TimeMarshaler startTime;
  private final TimeMarshaler duration;
  private final List<KeyValueMarshaler> tags;
  private final LogMarshaler[] logs;
  private final List<SpanRefMarshaler> references;

  static SpanMarshaler[] createRepeated(List<SpanData> spans) {
    int len = spans.size();
    SpanMarshaler[] marshalers = new SpanMarshaler[len];
    for (int i = 0; i < len; i++) {
      marshalers[i] = SpanMarshaler.create(spans.get(i));
    }
    return marshalers;
  }

  static SpanMarshaler create(SpanData span) {
    String traceId = span.getSpanContext().getTraceId();
    String spanId = span.getSpanContext().getSpanId();
    byte[] operationNameUtf8 = MarshalerUtil.toBytes(span.getName());
    TimeMarshaler startTime = TimeMarshaler.create(span.getStartEpochNanos());
    TimeMarshaler duration =
        TimeMarshaler.create(span.getEndEpochNanos() - span.getStartEpochNanos());

    List<KeyValueMarshaler> tags = KeyValueMarshaler.createRepeated(span.getAttributes());
    int droppedAttributes = span.getTotalAttributeCount() - span.getAttributes().size();
    if (droppedAttributes > 0) {
      tags.add(KeyValueMarshaler.create(KEY_DROPPED_ATTRIBUTES_COUNT, (long) droppedAttributes));
    }

    LogMarshaler[] logs = LogMarshaler.createRepeated(span.getEvents());
    int droppedEvents = span.getTotalRecordedEvents() - span.getEvents().size();
    if (droppedEvents > 0) {
      tags.add(KeyValueMarshaler.create(KEY_DROPPED_EVENTS_COUNT, (long) droppedEvents));
    }

    List<SpanRefMarshaler> references = SpanRefMarshaler.createRepeated(span.getLinks());

    // add the parent span
    SpanContext parentSpanContext = span.getParentSpanContext();
    if (parentSpanContext.isValid()) {
      references.add(SpanRefMarshaler.create(parentSpanContext));
    }

    if (span.getKind() != SpanKind.INTERNAL) {
      tags.add(
          KeyValueMarshaler.create(KEY_SPAN_KIND, span.getKind().name().toLowerCase(Locale.ROOT)));
    }

    if (!span.getStatus().getDescription().isEmpty()) {
      tags.add(
          KeyValueMarshaler.create(KEY_SPAN_STATUS_MESSAGE, span.getStatus().getDescription()));
    }

    if (span.getStatus().getStatusCode() != StatusCode.UNSET) {
      tags.add(
          KeyValueMarshaler.create(KEY_SPAN_STATUS_CODE, span.getStatus().getStatusCode().name()));
    }

    tags.add(
        KeyValueMarshaler.create(
            KEY_INSTRUMENTATION_SCOPE_NAME, span.getInstrumentationScopeInfo().getName()));
    // Include instrumentation library name for backwards compatibility
    tags.add(
        KeyValueMarshaler.create(
            KEY_INSTRUMENTATION_LIBRARY_NAME, span.getInstrumentationScopeInfo().getName()));

    if (span.getInstrumentationScopeInfo().getVersion() != null) {
      tags.add(
          KeyValueMarshaler.create(
              KEY_INSTRUMENTATION_SCOPE_VERSION, span.getInstrumentationScopeInfo().getVersion()));
      // Include instrumentation library name for backwards compatibility
      tags.add(
          KeyValueMarshaler.create(
              KEY_INSTRUMENTATION_LIBRARY_VERSION,
              span.getInstrumentationScopeInfo().getVersion()));
    }

    if (span.getStatus().getStatusCode() == StatusCode.ERROR) {
      tags.add(KeyValueMarshaler.create(KEY_ERROR, true));
    }

    return new SpanMarshaler(
        traceId, spanId, operationNameUtf8, startTime, duration, tags, logs, references);
  }

  SpanMarshaler(
      String traceId,
      String spanId,
      byte[] operationNameUtf8,
      TimeMarshaler startTime,
      TimeMarshaler duration,
      List<KeyValueMarshaler> tags,
      LogMarshaler[] logs,
      List<SpanRefMarshaler> references) {
    super(
        calculateSize(
            traceId, spanId, operationNameUtf8, startTime, duration, tags, logs, references));
    this.traceId = traceId;
    this.spanId = spanId;
    this.operationNameUtf8 = operationNameUtf8;
    this.startTime = startTime;
    this.duration = duration;
    this.tags = tags;
    this.logs = logs;
    this.references = references;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeTraceId(Span.TRACE_ID, traceId);
    output.serializeSpanId(Span.SPAN_ID, spanId);
    output.serializeString(Span.OPERATION_NAME, operationNameUtf8);
    output.serializeMessage(Span.START_TIME, startTime);
    output.serializeMessage(Span.DURATION, duration);
    output.serializeRepeatedMessage(Span.TAGS, tags);
    output.serializeRepeatedMessage(Span.LOGS, logs);
    output.serializeRepeatedMessage(Span.REFERENCES, references);
  }

  @SuppressWarnings("ParameterNumber")
  private static int calculateSize(
      String traceId,
      String spanId,
      byte[] operationNameUtf8,
      TimeMarshaler startTime,
      TimeMarshaler duration,
      List<KeyValueMarshaler> tags,
      LogMarshaler[] logs,
      List<SpanRefMarshaler> references) {
    int size = 0;
    size += MarshalerUtil.sizeTraceId(Span.TRACE_ID, traceId);
    size += MarshalerUtil.sizeSpanId(Span.SPAN_ID, spanId);
    size += MarshalerUtil.sizeBytes(Span.OPERATION_NAME, operationNameUtf8);
    size += MarshalerUtil.sizeMessage(Span.START_TIME, startTime);
    size += MarshalerUtil.sizeMessage(Span.DURATION, duration);
    size += MarshalerUtil.sizeRepeatedMessage(Span.TAGS, tags);
    size += MarshalerUtil.sizeRepeatedMessage(Span.LOGS, logs);
    size += MarshalerUtil.sizeRepeatedMessage(Span.REFERENCES, references);
    return size;
  }
}
