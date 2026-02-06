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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.internal.grpc.GrpcExporter;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Exports spans to Jaeger via gRPC, using Jaeger's protobuf model.
 *
 * @deprecated Use {@code OtlpGrpcSpanExporter} or {@code OtlpHttpSpanExporter} from <a
 *     href="https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp/all">opentelemetry-exporter-otlp</a>
 *     instead.
 */
@Deprecated
public final class JaegerGrpcSpanExporter implements SpanExporter {

  private static final String DEFAULT_HOST_NAME = "unknown";
  private static final AttributeKey<String> CLIENT_VERSION_KEY =
      AttributeKey.stringKey("jaeger.version");
  private static final String CLIENT_VERSION_VALUE = "opentelemetry-java";
  private static final AttributeKey<String> HOSTNAME_KEY = AttributeKey.stringKey("hostname");
  private static final String IP_DEFAULT = "0.0.0.0";
  // Visible for testing
  static final AttributeKey<String> IP_KEY = AttributeKey.stringKey("ip");

  private final GrpcExporter<PostSpansRequestMarshaler> delegate;

  // Jaeger-specific resource information
  private final Resource jaegerResource;

  JaegerGrpcSpanExporter(GrpcExporter<PostSpansRequestMarshaler> delegate) {
    this.delegate = delegate;

    String hostname;
    String ipv4;

    try {
      hostname = InetAddress.getLocalHost().getHostName();
      ipv4 = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      hostname = DEFAULT_HOST_NAME;
      ipv4 = IP_DEFAULT;
    }

    jaegerResource =
        Resource.builder()
            .put(CLIENT_VERSION_KEY, CLIENT_VERSION_VALUE)
            .put(IP_KEY, ipv4)
            .put(HOSTNAME_KEY, hostname)
            .build();
  }

  /**
   * Submits all the given spans in a single batch to the Jaeger collector.
   *
   * @param spans the list of sampled Spans to be exported.
   * @return the result of the operation
   */
  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    List<CompletableResultCode> results = new ArrayList<>();
    spans.stream()
        .collect(Collectors.groupingBy(SpanData::getResource))
        .forEach(
            (resource, spanData) ->
                results.add(delegate.export(buildRequest(resource, spanData), spanData.size())));

    return CompletableResultCode.ofAll(results);
  }

  private PostSpansRequestMarshaler buildRequest(Resource resource, List<SpanData> spans) {
    Resource mergedResource = jaegerResource.merge(resource);
    return PostSpansRequestMarshaler.create(spans, mergedResource);
  }

  /**
   * The Jaeger exporter does not batch spans, so this method will immediately return with success.
   *
   * @return always Success
   */
  @Override
  public CompletableResultCode flush() {
    return CompletableResultCode.ofSuccess();
  }

  /**
   * Returns a new builder instance for this exporter.
   *
   * @return a new builder instance for this exporter.
   */
  public static JaegerGrpcSpanExporterBuilder builder() {
    return new JaegerGrpcSpanExporterBuilder();
  }

  /**
   * Initiates an orderly shutdown in which preexisting calls continue but new calls are immediately
   * cancelled.
   */
  @Override
  public CompletableResultCode shutdown() {
    return delegate.shutdown();
  }

  // Visible for testing
  Resource getJaegerResource() {
    return jaegerResource;
  }
}
