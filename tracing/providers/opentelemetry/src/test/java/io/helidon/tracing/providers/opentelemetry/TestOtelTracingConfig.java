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

package io.helidon.tracing.providers.opentelemetry;

import java.util.List;
import java.util.Map;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.tracing.Span;
import io.helidon.tracing.SpanProcessorType;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;

class TestOtelTracingConfig {


    @Test
    void testTags() {
        var configSettings = """
                tracing:
                  service: tracing-test
                  global: false
                  int-tags:
                    i1: 4
                  boolean-tags:
                    b1 : true
                  tags:
                    t1: anything
                """;
        Config config = Config.just(ConfigSources.create(configSettings, MediaTypes.APPLICATION_YAML));

        try (TestSpanExporter testSpanExporter = new TestSpanExporter()) {

            OpenTelemetryTracer tracer = OpenTelemetryTracer.builder()
                    .config(config.get("tracing"))
                    .addTracerTag("i2", 2)
                    .addTracerTag("b2", false)
                    .addSpanProcessor(SimpleSpanProcessor.create(testSpanExporter))
                    .build();

            tracer.spanBuilder("test-span").start().end();

            List<SpanData> spanData = testSpanExporter.spanData(1);
            assertThat("Span", spanData, hasSize(1));
            SpanData span = spanData.getFirst();
            var attrs = span.getAttributes().asMap();

            assertThat("Attributes", attrs, allOf(
                    hasAttribute("i1", 4L),
                    hasAttribute("b1", true),
                    hasAttribute("t1", "anything"),
                    hasAttribute("i2", 2L),
                    hasAttribute("b2", false)));

        }
    }

    @Test
    void testVariousSettings() {
        var configSettings = """
                tracing:
                  service: tracing-test
                  global: false
                  protocol: https
                  host: myhost
                  port: 1234
                  path: /mypath
                """;
        Config config = Config.just(ConfigSources.create(configSettings, MediaTypes.APPLICATION_YAML));


        OpenTelemetryTracer tracer = OpenTelemetryTracer.builder()
                .config(config.get("tracing"))
                .build();

        tracer.spanBuilder("test-span").start().end();

        /*
        The OpenTelemetry object does not expose public APIs to retrieve its parts, but toString() reveals information
        we can check so although this is a bit fragile we can at least verify that our config settings took effect.
         */
        assertThat("OpenTelemetry",
                   tracer.prototype().openTelemetry().toString(),
                   allOf(containsString("tracerProvider=SdkTracerProvider"),
                         containsString("sampler=AlwaysOnSampler"),
                         containsString("spanProcessor=BatchSpanProcessor"),
                         containsString("spanExporter=OtlpGrpcSpanExporter"),
                         containsString("endpoint=https://myhost:1234/mypath"),
                         containsString("textMapPropagator=MultiTextMapPropagator{textMapPropagators=[W3CTraceContextPropagator, W3CBaggagePropagator]}")));

        String exampleOtelToString = """
            OpenTelemetrySdk{
                tracerProvider=SdkTracerProvider{clock=SystemClock{}, idGenerator=RandomIdGenerator{}, resource=Resource{schemaUrl=null, 
                    attributes={service.name="tracing-test", telemetry.sdk.language="java", telemetry.sdk.name="opentelemetry", telemetry.sdk.version="1.57.0"}}, 
                spanLimitsSupplier=SpanLimitsValue{maxNumberOfAttributes=128, maxNumberOfEvents=128, maxNumberOfLinks=128, maxNumberOfAttributesPerEvent=128, maxNumberOfAttributesPerLink=128, maxAttributeValueLength=2147483647},
                sampler=AlwaysOnSampler,
                spanProcessor=BatchSpanProcessor{
                    spanExporter=OtlpGrpcSpanExporter{
                        endpoint=https://myhost:1234/mypath,
                        endpointPath=/opentelemetry.proto.collector.trace.v1.TraceService/Export,
                        timeoutNanos=10000000000,
                        connectTimeoutNanos=10000000000,
                        compressorEncoding=null,
                        headers=Headers{
                            User-Agent=OBFUSCATED}, 
                            retryPolicy=RetryPolicy{maxAttempts=5, initialBackoff=PT1S, maxBackoff=PT5S, backoffMultiplier=1.5, retryExceptionPredicate=null}, 
                        componentLoader=ServiceLoaderComponentLoader{classLoader=jdk.internal.loader.ClassLoaders$AppClassLoader@2c854dc5}, 
                        exporterType=OTLP_GRPC_SPAN_EXPORTER,
                        internalTelemetrySchemaVersion=LEGACY,
                        memoryMode=REUSABLE_DATA},
                    exportUnsampledSpans=false, scheduleDelayNanos=5000000000, maxExportBatchSize=512, exporterTimeoutNanos=10000000000}, 
                    tracerConfigurator=ScopeConfiguratorImpl{conditions=[]}}, 
                    meterProvider=SdkMeterProvider{clock=SystemClock{}, resource=Resource{schemaUrl=null,
                        attributes={service.name="unknown_service:java", telemetry.sdk.language="java", telemetry.sdk.name="opentelemetry", telemetry.sdk.version="1.57.0"}},
                    metricReaders=[], metricProducers=[], views=[], meterConfigurator=ScopeConfiguratorImpl{conditions=[]}},
                loggerProvider=SdkLoggerProvider{clock=SystemClock{}, resource=Resource{schemaUrl=null, 
                    attributes={service.name="unknown_service:java", telemetry.sdk.language="java", telemetry.sdk.name="opentelemetry", telemetry.sdk.version="1.57.0"}},
                    logLimits=LogLimits{maxNumberOfAttributes=128, maxAttributeValueLength=2147483647}, logRecordProcessor=NoopLogRecordProcessor, loggerConfigurator=ScopeConfiguratorImpl{conditions=[]}},
                propagators=DefaultContextPropagators{textMapPropagator=MultiTextMapPropagator{textMapPropagators=[W3CTraceContextPropagator, W3CBaggagePropagator]}}}""";
}

    @Test
    void testHttpExporterTypeSetting() {
        var configSettings = """
                tracing:
                  service: tracing-test
                  global: false
                  exporter-type: http/proto
                  protocol: https
                  host: myhost
                  port: 1234
                  path: /mypath
                """;
        Config config = Config.just(ConfigSources.create(configSettings, MediaTypes.APPLICATION_YAML));

        OpenTelemetryTracer tracer = OpenTelemetryTracer.builder()
                .config(config.get("tracing"))
                .build();

        tracer.spanBuilder("test-span").start().end();

        /*
        The OpenTelemetry object does not expose public APIs to retrieve its parts, but toString() reveals information
        we can check so although this is a bit fragile we can at least verify that our config settings took effect.
         */
        assertThat("OpenTelemetry",
                   tracer.prototype().openTelemetry().toString(),
                   containsString("spanExporter=OtlpHttpSpanExporter"));
    }


    private static Matcher<Map<? extends AttributeKey<?>, ?>> hasAttribute(String key, Object value) {
        return switch (value) {
            case String sValue -> hasEntry(AttributeKey.stringKey(key), sValue);
            case Boolean b -> hasEntry(AttributeKey.booleanKey(key), b);
            case Long b -> hasEntry(AttributeKey.longKey(key), b);
            case Double b -> hasEntry(AttributeKey.doubleKey(key), b);
            case Float b -> hasEntry(AttributeKey.doubleKey(key), b.doubleValue());
            case Byte b -> hasEntry(AttributeKey.longKey(key), (long) b);
            default -> hasEntry(AttributeKey.stringKey(key), value);
        };
    }

}
