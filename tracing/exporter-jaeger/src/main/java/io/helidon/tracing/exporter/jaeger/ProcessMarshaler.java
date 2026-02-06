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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.MarshalerWithSize;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.sdk.resources.Resource;

final class ProcessMarshaler extends MarshalerWithSize {

  private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

  private final byte[] serviceNameUtf8;
  private final List<KeyValueMarshaler> tags;

  static ProcessMarshaler create(Resource resource) {
    String serviceName = resource.getAttribute(SERVICE_NAME);
    if (serviceName == null || serviceName.isEmpty()) {
      serviceName = Resource.getDefault().getAttribute(SERVICE_NAME);
    }

    return new ProcessMarshaler(
        MarshalerUtil.toBytes(serviceName),
        KeyValueMarshaler.createRepeated(resource.getAttributes()));
  }

  ProcessMarshaler(byte[] serviceNameUtf8, List<KeyValueMarshaler> tags) {
    super(calculateSize(serviceNameUtf8, tags));
    this.serviceNameUtf8 = serviceNameUtf8;
    this.tags = tags;
  }

  @Override
  protected void writeTo(Serializer output) throws IOException {
    output.serializeString(Process.SERVICE_NAME, serviceNameUtf8);
    output.serializeRepeatedMessage(Process.TAGS, tags);
  }

  private static int calculateSize(byte[] serviceNameUtf8, List<KeyValueMarshaler> tags) {
    int size = 0;
    size += MarshalerUtil.sizeBytes(Process.SERVICE_NAME, serviceNameUtf8);
    size += MarshalerUtil.sizeRepeatedMessage(Process.TAGS, tags);
    return size;
  }
}
