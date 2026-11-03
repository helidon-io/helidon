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

package io.helidon.telemetry.otelconfig;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.exporter.zipkin.ZipkinSpanExporter;
import io.helidon.webserver.WebServer;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.Sender;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@SuppressWarnings({"deprecation", "removal"})
class TestZipkinCompatibility {
    @Test
    void existingYamlConfigurationExportsThroughWebClient() throws Exception {
        var requests = new LinkedBlockingQueue<byte[]>();
        WebServer server = WebServer.builder().port(0)
                .routing(routes -> routes.post("/collector", (request, response) -> {
                    requests.add(request.content().as(byte[].class));
                    response.status(202).send();
                })).build().start();
        try {
            Config config = Config.just(ConfigSources.create("""
                    telemetry:
                      service: existing-service
                      global: false
                      signals:
                        tracing:
                          exporters:
                            - type: zipkin
                              endpoint: http://localhost:%d/collector
                              encoder: JSON_V2
                              compression: none
                              timeout: PT2S
                          processors:
                            - type: simple
                    """.formatted(server.port()), MediaTypes.APPLICATION_YAML));
            OpenTelemetryConfig telemetry = OpenTelemetryConfig.create(config.get("telemetry"));
            try (OpenTelemetrySdk sdk = telemetry.openTelemetrySdk()) {
                sdk.getTracer("compatibility").spanBuilder("existing-config-span").startSpan().end();
                byte[] body = requests.poll(5, TimeUnit.SECONDS);
                assertThat(body, notNullValue());
                var spans = SpanBytesDecoder.JSON_V2.decodeList(body);
                assertThat(spans, hasSize(1));
                assertThat(spans.getFirst().name(), is("existing-config-span"));
                assertThat(spans.getFirst().localServiceName(), is("existing-service"));
            }
        } finally {
            server.stop();
        }
    }

    @Test
    void existingProgrammaticSenderAndEncoderRemainSupported() {
        AtomicInteger closes = new AtomicInteger();
        Sender sender = new Sender() {
            @Override
            public zipkin2.reporter.Encoding encoding() {
                return zipkin2.reporter.Encoding.JSON;
            }

            @Override
            public int messageMaxBytes() {
                return 500_000;
            }

            @Override
            public int messageSizeInBytes(List<byte[]> spans) {
                return 0;
            }

            @Override
            public zipkin2.reporter.Call<Void> sendSpans(List<byte[]> spans) {
                return zipkin2.reporter.Call.create(null);
            }

            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        ZipkinExporterConfig config = ZipkinExporterConfig.builder().sender(sender)
                .encoder(SpanBytesEncoder.JSON_V2).compression(CompressionType.NONE)
                .endpoint(URI.create("http://unused.example.com/api/v2/spans"))
                .timeout(Duration.ofSeconds(2)).localIpAddressSupplier(() -> null).build();
        try (var exporter = ZipkinSpanExporter.builder().sender(config.sender().orElseThrow())
                .encoder(config.encoder().orElseThrow()).localIpAddressSupplier(config.localIpAddressSupplier().orElseThrow())
                .build()) {
            assertThat(exporter.export(List.of()).isSuccess(), is(true));
        }
        assertThat(closes.get(), is(1));
    }
}
