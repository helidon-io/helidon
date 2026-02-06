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
