/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.helidon.tracing.opentelemetrycompat.jaegerexporter;

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
