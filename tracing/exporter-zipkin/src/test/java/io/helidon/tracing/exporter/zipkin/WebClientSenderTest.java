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

package io.helidon.tracing.exporter.zipkin;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.http.HeaderNames;
import io.helidon.webserver.WebServer;

import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.codec.SpanBytesDecoder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("removal")
class WebClientSenderTest {
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private WebServer server;
    private volatile int status;
    private volatile String redirect;
    private volatile boolean delayHeaders;
    private volatile boolean stallBody;

    @BeforeEach
    void startServer() {
        status = 202;
        server = WebServer.builder().port(0).routing(routing -> routing.any((request, response) -> {
            byte[] body = request.content().hasEntity() ? request.content().as(byte[].class) : new byte[0];
            requests.add(new Request(request.path().path(), request.prologue().method().text(), body,
                                     request.headers().first(HeaderNames.CONTENT_ENCODING).orElse(""),
                                     request.headers().first(HeaderNames.CONTENT_TYPE).orElse(""),
                                     request.headers().first(HeaderNames.create("b3")).orElse("")));
            if (delayHeaders) {
                Thread.sleep(200);
            }
            if (stallBody) {
                response.headers().contentLength(2);
                var output = response.outputStream();
                output.write('a');
                output.flush();
                Thread.sleep(200);
                output.write('b');
                output.close();
                return;
            }
            if (redirect != null && !request.path().path().equals("/redirected")) {
                response.header(HeaderNames.LOCATION, redirect).status(status).send();
            } else {
                response.status(redirect == null ? status : 202).send();
            }
        })).build().start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void exportsJsonGzipAndSuppressionHeader() throws Exception {
        try (var exporter = exporter().build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
            Request request = next();
            assertThat(request.path(), is("/custom/api/v2/spans"));
            assertThat(request.method(), is("POST"));
            assertThat(request.compression(), is("gzip"));
            assertThat(request.contentType(), is("application/json"));
            assertThat(request.b3(), is("0"));
            var spans = SpanBytesDecoder.JSON_V2.decodeList(request.body());
            assertThat(spans, hasSize(1));
            assertThat(spans.getFirst().localServiceName(), is("test-service"));
            assertThat(spans.getFirst().name(), is("operation"));
        }
    }

    @Test
    void exportsProtobufWithoutCompression() throws Exception {
        Config config = Config.just(ConfigSources.create(Map.of("endpoint", endpoint().toString(),
                                                                "encoder", "PROTO3", "compression", "none")));
        try (var exporter = ZipkinSpanExporter.create(config)) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
            Request request = next();
            assertThat(request.compression(), is(""));
            assertThat(request.contentType(), is("application/x-protobuf"));
            assertThat(SpanBytesDecoder.PROTO3.decodeList(request.body()), hasSize(1));
        }
    }

    @Test
    void supportsLegacyEncoderAndEmptyBatch() throws Exception {
        try (var exporter = exporter().encoder(zipkin2.codec.SpanBytesEncoder.JSON_V2).build()) {
            assertThat(exporter.export(List.of()).isSuccess(), is(true));
            assertThat(SpanBytesDecoder.JSON_V2.decodeList(next().body()), hasSize(0));
        }
    }

    @Test
    void collectorErrorsFailWithoutLeakingPayload() throws Exception {
        status = 503;
        try (var exporter = exporter().build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            assertThat(next(), notNullValue());
            status = 202;
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
        }
    }

    @Test
    void followsPost302AsGet() throws Exception {
        status = 302;
        redirect = "/redirected";
        try (var exporter = exporter().build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
            assertThat(next().method(), is("POST"));
            Request followup = next();
            assertThat(followup.method(), is("GET"));
            assertThat(followup.path(), is("/redirected"));
            assertThat(followup.body().length, is(0));
        }
    }

    @Test
    void doesNotReplayPostOn307() throws Exception {
        status = 307;
        redirect = "/redirected";
        try (var exporter = exporter().build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            assertThat(next().method(), is("POST"));
            assertThat(requests.size(), is(0));
        }
    }

    @Test
    void sdkProcessorExportsRealSpans() throws Exception {
        try (var exporter = exporter().build();
             var provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            provider.get("test").spanBuilder("sdk-span").startSpan().end();
            var spans = SpanBytesDecoder.JSON_V2.decodeList(next().body());
            assertThat(spans.getFirst().name(), is("sdk-span"));
        }
    }

    @Test
    void autoConfigurationDiscoversZipkinProvider() throws Exception {
        var sdk = AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesSupplier(() -> Map.of("otel.traces.exporter", "zipkin",
                                                    "otel.exporter.zipkin.endpoint", endpoint().toString(),
                                                    "otel.exporter.zipkin.timeout", "2500",
                                                    "otel.metrics.exporter", "none",
                                                    "otel.logs.exporter", "none"))
                .build().getOpenTelemetrySdk();
        try (sdk) {
            sdk.getTracer("test").spanBuilder("auto-configured").startSpan().end();
            assertThat(sdk.getSdkTracerProvider().forceFlush().join(5, TimeUnit.SECONDS).isSuccess(), is(true));
            assertThat(SpanBytesDecoder.JSON_V2.decodeList(next().body()).getFirst().name(), is("auto-configured"));
        }
    }

    @Test
    void suppressesInstrumentationForCustomSenderAndRestoresContext() {
        var sender = new ZipkinSpanExporterTest.TestSender() {
            @Override
            public void send(List<byte[]> spans) {
                assertThat(InstrumentationUtil.shouldSuppressInstrumentation(Context.current()), is(true));
            }
        };
        try (var exporter = exporter().sender(sender).build()) {
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
        }
        assertThat(InstrumentationUtil.shouldSuppressInstrumentation(Context.current()), is(false));
    }

    @Test
    void validatesIndependentConstraintsTogetherWithoutEchoingEndpoint() {
        try {
            ZipkinSpanExporter.builder().endpoint(URI.create("ftp:/sensitive-path"))
                    .compression("unsupported").timeout(Duration.ofSeconds(-1)).build();
            throw new AssertionError("Expected configuration validation failure");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("endpoint must use"));
            assertThat(e.getMessage(), containsString("endpoint must contain a host"));
            assertThat(e.getMessage(), containsString("compression must be"));
            assertThat(e.getMessage(), containsString("timeout must not be negative"));
            assertThat(e.getMessage().contains("sensitive-path"), is(false));
        }
    }

    @Test
    void normalizesTimeoutsLikeUpstream() {
        assertThat(ZipkinExporterSupport.readTimeout(Duration.ZERO), is(Duration.ofMillis(Integer.MAX_VALUE)));
        assertThat(ZipkinExporterSupport.readTimeout(Duration.ofNanos(1)), is(Duration.ofMillis(Integer.MAX_VALUE)));
        assertThat(ZipkinExporterSupport.readTimeout(Duration.ofSeconds(Long.MAX_VALUE)),
                   is(Duration.ofMillis(Integer.MAX_VALUE)));
        assertThat(ZipkinExporterSupport.readTimeout(Duration.ofNanos(1_999_999)), is(Duration.ofMillis(1)));
    }

    @Test
    void readTimeoutAppliesToDelayedHeadersAndStalledBody() {
        try (var exporter = exporter().timeout(Duration.ofMillis(30)).build()) {
            delayHeaders = true;
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            delayHeaders = false;
            stallBody = true;
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(false));
            stallBody = false;
            assertThat(exporter.export(List.of(ZipkinTestSpans.span().build())).isSuccess(), is(true));
        }
    }

    @Test
    void malformedEndpointDoesNotExposeItsValueAndOtherChecksStillRun() {
        var error = assertThrows(RuntimeException.class, () -> ZipkinSpanExporter.create(Config.just(
                ConfigSources.create(Map.of("endpoint", "http://sensitive value", "compression", "invalid",
                                            "timeout", "-PT1S")))));
        assertThat(error.getMessage(), containsString("endpoint must be a syntactically valid URI"));
        assertThat(error.getMessage(), containsString("compression must be gzip or none"));
        assertThat(error.getMessage(), containsString("timeout must not be negative"));
        assertThat(error.getMessage().contains("sensitive value"), is(false));
    }

    @Test
    void openTelemetryProviderAccumulatesIndependentFailures() {
        var error = assertThrows(RuntimeException.class, () -> AutoConfiguredOpenTelemetrySdk.builder()
                .disableShutdownHook()
                .addPropertiesSupplier(() -> Map.of("otel.traces.exporter", "zipkin",
                                                    "otel.exporter.zipkin.endpoint", "ftp:/sensitive-path",
                                                    "otel.exporter.zipkin.timeout", "sensitive-invalid-duration",
                                                    "otel.metrics.exporter", "none", "otel.logs.exporter", "none"))
                .build());
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage());
        }
        assertThat(messages.toString(), containsString("endpoint must use the http or https scheme"));
        assertThat(messages.toString(), containsString("otel.exporter.zipkin.timeout must be a valid duration"));
        assertThat(messages.toString().contains("sensitive-path"), is(false));
        assertThat(messages.toString().contains("sensitive-invalid-duration"), is(false));
    }

    @Test
    void acceptsTheSameUnderscoreHostsAsWebClient() {
        var config = ZipkinSpanExporter.builder().endpoint("http://collector_name.example:9411/api/v2/spans").buildPrototype();
        assertThat(config.endpoint(), is("http://collector_name.example:9411/api/v2/spans"));
    }

    private ZipkinSpanExporterConfig.Builder exporter() {
        return ZipkinSpanExporter.builder().endpoint(endpoint());
    }

    private URI endpoint() {
        return URI.create("http://localhost:" + server.port() + "/custom/api/v2/spans");
    }

    private Request next() throws InterruptedException {
        Request request = requests.poll(5, TimeUnit.SECONDS);
        assertThat("collector request", request, notNullValue());
        return request;
    }

    private record Request(String path, String method, byte[] body, String compression, String contentType, String b3) {
    }
}
