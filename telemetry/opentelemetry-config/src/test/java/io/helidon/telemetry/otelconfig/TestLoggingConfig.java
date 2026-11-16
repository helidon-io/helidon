/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.testing.junit5.OptionalMatcher;
import io.helidon.config.Config;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogLimits;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

class TestLoggingConfig {

    private static final List<String> LOGGER_NAMES = List.of("first-logger", "second-logger");
    private static final List<String> ALL_RECORDS = List.of("debug-sampled", "warn-sampled", "error-sampled",
                                                           "error-unsampled", "info-no-trace", "warn-no-trace",
                                                           "unspecified-no-trace");

    @Test
    void testLoggingConfig() {
        var config = Config.just("""
                                         telemetry:
                                           service: test-tel-logging
                                           global: false
                                           signals:
                                             logging:
                                               minimum-severity: TRACE
                                               log-limits:
                                                 max-attribute-value-length: 20
                                                 max-number-of-attributes: 14
                                               processors:
                                                 - type: batch
                                                   schedule-delay: PT10S
                                                   max-queue-size: 15
                                                   max-export-batch-size: 5
                                                   timeout: PT30S
                                                 - type: simple
                                               exporters:
                                                 - name: exp-1
                                                   endpoint: "http://host:1234"
                                         """,
                                 MediaTypes.APPLICATION_YAML);

        var otelConfig = OpenTelemetryConfig.create(config.get("telemetry"));

        assertThat("Logging in OTel config", otelConfig.logging(), OptionalMatcher.optionalPresent());

        OpenTelemetryLoggingConfig loggingConfig = otelConfig.logging().get();

        assertThat("Logging processors", loggingConfig.processors(), hasSize(2));

        assertThat("Logging processor", loggingConfig.processors().getFirst().toString(),
                   allOf(containsString("endpoint=http://host:1234"),
                         containsString("")));

        assertThat("Log limits", loggingConfig.logLimits(),
                   OptionalMatcher.optionalValue(allOf(maxNumberOfAttributes(is(14)),
                                                       maxAttributeValueLength(is(20)))));

        assertThat("Severity", loggingConfig.minimumSeverity(), OptionalMatcher.optionalValue(is(Severity.TRACE)));


    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testLoggingDefaults(ConfigurationStyle style) {
        assertExportedRecords(style, "{}", _ -> { }, ALL_RECORDS);
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testLoggingDisabled(ConfigurationStyle style) {
        assertExportedRecords(style, "enabled: false", builder -> builder.enabled(false), List.of());
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testLoggingEnabled(ConfigurationStyle style) {
        assertExportedRecords(style, "enabled: true", builder -> builder.enabled(true), ALL_RECORDS);
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testMinimumSeverity(ConfigurationStyle style) {
        assertExportedRecords(style, "minimum-severity: WARN", builder -> builder.minimumSeverity(Severity.WARN),
                              List.of("warn-sampled", "error-sampled", "error-unsampled", "warn-no-trace",
                                      "unspecified-no-trace"));
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testTraceBased(ConfigurationStyle style) {
        assertExportedRecords(style, "trace-based: true", builder -> builder.traceBased(true),
                              List.of("debug-sampled", "warn-sampled", "error-sampled", "info-no-trace", "warn-no-trace",
                                      "unspecified-no-trace"));
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testTraceBasedDisabled(ConfigurationStyle style) {
        assertExportedRecords(style, "trace-based: false", builder -> builder.traceBased(false), ALL_RECORDS);
    }

    @ParameterizedTest
    @EnumSource(ConfigurationStyle.class)
    void testCombinedFilters(ConfigurationStyle style) {
        assertExportedRecords(style, """
                minimum-severity: WARN
                trace-based: true
                """, builder -> builder.minimumSeverity(Severity.WARN).traceBased(true),
                              List.of("warn-sampled", "error-sampled", "warn-no-trace", "unspecified-no-trace"));
    }

    static Matcher<LogLimits> maxNumberOfAttributes(Matcher<? super Integer> matcher) {
        return new FeatureMatcher<LogLimits, Integer>(matcher, "an integer value that ","maxNumberOfAttributes") {
            @Override
            protected Integer featureValueOf(LogLimits actual) {
                return actual.getMaxNumberOfAttributes();
            }
        };
    }

    static Matcher<LogLimits> maxAttributeValueLength(Matcher<? super Integer> matcher) {
        return new FeatureMatcher<LogLimits, Integer>(matcher, "an integer value that ","maxAttributeValueLength") {
            @Override
            protected Integer featureValueOf(LogLimits actual) {
                return actual.getMaxAttributeValueLength();
            }
        };
    }

    private static void assertExportedRecords(ConfigurationStyle style,
                                             String yaml,
                                             Consumer<OpenTelemetryLoggingConfig.Builder> configure,
                                             List<String> expectedBodies) {
        var telemetry = OpenTelemetryConfig.builder().service("logging-filters-test").global(false);
        var logging = OpenTelemetryLoggingConfig.builder();
        if (style == ConfigurationStyle.YAML) {
            telemetry.config(Config.just("signals:\n  logging:\n" + yaml.indent(4), MediaTypes.APPLICATION_YAML));
            logging = OpenTelemetryLoggingConfig.builder(telemetry.logging().orElseThrow());
        } else {
            configure.accept(logging);
        }

        var exporter = new CapturingLogRecordExporter();
        telemetry.logging(logging.addProcessor(SimpleLogRecordProcessor.create(exporter)).build());
        try (var sdk = telemetry.buildPrototype().openTelemetrySdk()) {
            emitRecords(sdk.getSdkLoggerProvider());

            var expected = LOGGER_NAMES.stream()
                    .flatMap(name -> expectedBodies.stream().map(body -> name + ":" + body))
                    .toList();
            var actual = exporter.records.stream()
                    .map(record -> record.getInstrumentationScopeInfo().getName() + ":" + record.getBodyValue().asString())
                    .toList();
            assertThat("Exported records", actual, is(expected));
        }
    }

    private static void emitRecords(SdkLoggerProvider provider) {
        var sampled = traceContext(TraceFlags.getSampled());
        var unsampled = traceContext(TraceFlags.getDefault());
        for (var name : LOGGER_NAMES) {
            var logger = provider.get(name);
            logger.logRecordBuilder().setBody("debug-sampled").setSeverity(Severity.DEBUG).setContext(sampled).emit();
            logger.logRecordBuilder().setBody("warn-sampled").setSeverity(Severity.WARN).setContext(sampled).emit();
            logger.logRecordBuilder().setBody("error-sampled").setSeverity(Severity.ERROR).setContext(sampled).emit();
            logger.logRecordBuilder().setBody("error-unsampled").setSeverity(Severity.ERROR).setContext(unsampled).emit();
            logger.logRecordBuilder().setBody("info-no-trace").setSeverity(Severity.INFO).setContext(Context.root()).emit();
            logger.logRecordBuilder().setBody("warn-no-trace").setSeverity(Severity.WARN).setContext(Context.root()).emit();
            logger.logRecordBuilder().setBody("unspecified-no-trace").setContext(Context.root()).emit();
        }
    }

    private static Context traceContext(TraceFlags flags) {
        return Context.root().with(Span.wrap(SpanContext.create("11111111111111111111111111111111",
                                                               "1111111111111111",
                                                               flags,
                                                               TraceState.getDefault())));
    }

    private enum ConfigurationStyle {
        YAML,
        PROGRAMMATIC
    }

    private static final class CapturingLogRecordExporter implements LogRecordExporter {
        private final List<LogRecordData> records = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<LogRecordData> logs) {
            records.addAll(logs);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
