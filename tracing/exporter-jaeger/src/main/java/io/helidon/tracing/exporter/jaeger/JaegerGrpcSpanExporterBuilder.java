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

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import io.grpc.ManagedChannel;
import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.internal.grpc.GrpcExporterBuilder;
import io.opentelemetry.sdk.internal.StandardComponentId;

import static io.opentelemetry.api.internal.Utils.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Builder utility for this exporter.
 *
 * @deprecated Use {@code OtlpGrpcSpanExporter} or {@code OtlpHttpSpanExporter} from <a
 *     href="https://github.com/open-telemetry/opentelemetry-java/tree/main/exporters/otlp/all">opentelemetry-exporter-otlp</a>
 *     instead.
 */
@Deprecated
public final class JaegerGrpcSpanExporterBuilder {

  private static final String GRPC_SERVICE_NAME = "jaeger.api_v2.CollectorService";

  // Visible for testing
  static final String GRPC_ENDPOINT_PATH = "/" + GRPC_SERVICE_NAME + "/PostSpans";

  private static final String DEFAULT_ENDPOINT_URL = "http://localhost:14250";
  private static final URI DEFAULT_ENDPOINT = URI.create(DEFAULT_ENDPOINT_URL);
  private static final long DEFAULT_TIMEOUT_SECS = 10;

  private final GrpcExporterBuilder<PostSpansRequestMarshaler> delegate;

  JaegerGrpcSpanExporterBuilder() {
    delegate =
        new GrpcExporterBuilder<>(
                StandardComponentId.ExporterType.OTLP_GRPC_SPAN_EXPORTER,
                DEFAULT_TIMEOUT_SECS,
                DEFAULT_ENDPOINT,
                () -> MarshalerCollectorServiceGrpc::newFutureStub,
                GRPC_ENDPOINT_PATH);
  }

  /**
   * Sets the managed channel to use when communicating with the backend. Takes precedence over
   * {@link #setEndpoint(String)} if both are called.
   *
   * @param channel the channel to use.
   * @return this.
   * @deprecated Use {@link #setEndpoint(String)}. If you have a use case not satisfied by the
   *     methods on this builder, please file an issue to let us know what it is.
   */
  @Deprecated
  public JaegerGrpcSpanExporterBuilder setChannel(ManagedChannel channel) {
    delegate.setChannel(channel);
    return this;
  }

  /**
   * Sets the Jaeger endpoint to connect to. If unset, defaults to {@value DEFAULT_ENDPOINT_URL}.
   * The endpoint must start with either http:// or https://.
   *
   * @param endpoint endpoint
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setEndpoint(String endpoint) {
    requireNonNull(endpoint, "endpoint");
    delegate.setEndpoint(endpoint);
    return this;
  }

  /**
   * Sets the method used to compress payloads. If unset, compression is disabled. Currently
   * supported compression methods include "gzip" and "none".
   *
   * @param compressionMethod compression method
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setCompression(String compressionMethod) {
    requireNonNull(compressionMethod, "compressionMethod");
    checkArgument(
        compressionMethod.equals("gzip") || compressionMethod.equals("none"),
        "Unsupported compression method. Supported compression methods include: gzip, none.");
    delegate.setCompression(compressionMethod);
    return this;
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of spans. If
   * unset, defaults to {@value DEFAULT_TIMEOUT_SECS}s.
   *
   * @param timeout timeout
   * @param unit unit
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setTimeout(long timeout, TimeUnit unit) {
    requireNonNull(unit, "unit");
    checkArgument(timeout >= 0, "timeout must be non-negative");
    delegate.setTimeout(timeout, unit);
    return this;
  }

  /**
   * Sets the maximum time to wait for the collector to process an exported batch of spans. If
   * unset, defaults to {@value DEFAULT_TIMEOUT_SECS}s.
   *
   * @param timeout timeout
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setTimeout(Duration timeout) {
    requireNonNull(timeout, "timeout");
    delegate.setTimeout(timeout);
    return this;
  }

  /**
   * Sets the certificate chain to use for verifying servers when TLS is enabled. The {@code byte[]}
   * should contain an X.509 certificate collection in PEM format. If not set, TLS connections will
   * use the system default trusted certificates.
   *
   * @param trustedCertificatesPem trust certificate
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setTrustedCertificates(byte[] trustedCertificatesPem) {
    delegate.setTrustManagerFromCerts(trustedCertificatesPem);
    return this;
  }

  /** Sets the client key and chain to use for verifying servers when mTLS is enabled.
   *
   * @param privateKeyPem private key
   * @param certificatePem certificate
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setClientTls(byte[] privateKeyPem, byte[] certificatePem) {
    delegate.setKeyManagerFromCerts(privateKeyPem, certificatePem);
    return this;
  }

  /**
   * Sets the "bring-your-own" SSLContext for use with TLS. Users should call this _or_ set raw
   * certificate bytes, but not both.
   *
   * @param sslContext SSL context
   * @param trustManager trust manager
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setSslContext(
      SSLContext sslContext, X509TrustManager trustManager) {
    delegate.setSslContext(sslContext, trustManager);
    return this;
  }

  /**
   * Sets the {@link io.opentelemetry.api.metrics.MeterProvider} to use to collect metrics related to export. If not set, uses
   * {@link io.opentelemetry.api.GlobalOpenTelemetry#getMeterProvider()}.
   *
   * @param meterProvider meter provider
   * @return updated builder
   */
  public JaegerGrpcSpanExporterBuilder setMeterProvider(MeterProvider meterProvider) {
    requireNonNull(meterProvider, "meterProvider");
    delegate.setMeterProvider(() -> meterProvider);
    return this;
  }

  /**
   * Constructs a new instance of the exporter based on the builder's values.
   *
   * @return a new exporter's instance.
   */
  public JaegerGrpcSpanExporter build() {
    return new JaegerGrpcSpanExporter(delegate.build());
  }
}
