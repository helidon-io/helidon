/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.telemetry.testing.tracing;

import java.util.List;
import java.util.Map;

/**
 * Test utility class which converts JSON in a {@linkplain java.util.logging.Logger logger}, emitted by the OpenTelemetry
 * {@code logger_otlp} {@linkplain io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter exporter},
 * into the more convenient {@link JsonLogConverterImpl.LogResourceScopeSpans} and
 * related structures for ease of checking the exported tracing information.
 * <p>
 * To use this class:
 * <ol>
 *     <li>Set up test configuration similar to this: {@snippet lang = yaml:
 * telemetry:
 *   service: "otel-config-example"
 *   signals:
 *     tracing:
 *       processors:
 *         - type: simple
 *       exporters:
 *         - type: logging_otlp
 *}
 * Using {@code processors.type: simple} avoids batching of span data so span data becomes accessible more quickly,
 * and using {@code exporters.type: logging_otlp} directs the span data to the JUL logger as JSON text.
 *     </li>
 *     <li>Invoke the static {@link #create()} method on this type (ideally in a try-with-resource block).</li>
 *     <li>Execute test code that causes OTel to create one or more spans</li>
 *     <li>Invoke the instance {@link #resourceSpans(int)} method to wait for and retrieve the expected number of spans.</li>
 * </ol>
 * <p>
 * The OTel {@code io.opentelemetry.exporter.internal.otlp.traces.ResourceSpansMarshaler} class organizes the log data into
 * the JSON structure outlined below. This Javadoc excludes some elements that might appear in the log that this test utility
 * class does not support.
 * <p>
 * The {@code logging-otlp} exporter seems to emit a JSON block--and therefore this class creates a {@code LogResourceScopeSpans}
 * instance--for each tracing span.
 * <ul>
 *     <li>{@code resource} (see {@code io.opentelemetry.exporter.internal.otlp.ResourceMarshaler})
 *        <ul>
 *            <li>{@code attributes} array (see {@code io.opentelemetry.exporter.internal.otlp.KeyValueMarshaler})
 *                <p>
 *                Various marshalers (some private to {@code KeyValueMarshaler} deal with their respective datatypes:
 *                <ul>
 *                    <li>{@code stringValue}</li>
 *                    <li>{@code boolValue}</li>
 *                    <li>{@code intValue}</li>
 *                    <li>{@code doubleValue}</li>
 *                </ul>
 *            </li>
 *        </ul>
 *     </li>
 *     <li>{@code scopeSpans} (see {@code io.opentelemetry.exporter.internal.otlp.traces.InstrumentationScopeSpansMarshaler}
 *        <ul>
 *            <li>{@code scope}
 *            <ul>
 *                <li>{@code name}</li>
 *                <li>{@code attributes} array (see above)</li>
 *            </ul>
 *            </li>
 *        </ul>
 *        <ul>
 *            <li>{@code spans} array (again, some potential items might appear that this class does not currently support)
 *            <ul>
 *                <li>{@code traceId}</li>
 *                <li>{@code spanId}</li>
 *                <li>{@code parentSpanId}</li>
 *                <li>{@code name}</li>
 *                <li>{@code kind} (an integer)</li>
 *                <li>{@code startTimeUnixNanos}</li>
 *                <li>{@code endTimeUnixNanos}</li>
 *                <li>{@code attributes} array (as above)</li>
 *            </ul>
 *            </li>
 *        </ul>
 *     </li>
 * </ul>
 */
public interface JsonLogConverter extends AutoCloseable {

    /**
     * Creates an instance of the converter that captures spans reported by OTel.
     *
     * @return new {@code JsonLogConverterImpl} instance
     */
    static JsonLogConverter create() {
        return new JsonLogConverterImpl();
    }

    /**
     * Returns {@link JsonLogConverterImpl.LogResourceScopeSpans} instances
     * corresponding to the data emitted by the log exporter.
     *
     * @param expectedCount exact number of spans expected from a given test
     * @return info converted from the JSON in the logger
     */
    List<LogResourceScopeSpans> resourceSpans(int expectedCount);

    /**
     * Top-level aggregation of resource and scope-spans information.
     */
    interface LogResourceScopeSpans {
        /**
         * Returns the {@link io.helidon.telemetry.testing.tracing.JsonLogConverterImpl.LogResource} portion of the
         * top-level information.
         *
         * @return resource information
         */
        LogResource resource();

        /**
         * Returns the {@link io.helidon.telemetry.testing.tracing.JsonLogConverter.LogScopeSpans} portion of the top-level
         * information.
         *
         * @return scope and spans information
         */
        List<? extends LogScopeSpans> scopeSpans();
    }

    /**
     * Top-level resource data for emitted span data.
     * <p>
     * Each attribute appears in the emitted JSON like this:
     * {@snippet lang = json :
     * "key": "attr-name"
     * "value": {
            "intValue": "9"}
     *}
     * where the key under {@code value} (such as {@code intValue} indicates the type of the value.
     */
    interface LogResource {

        /**
         * Returns the attributes stored in the {@code LogResource}.
         *
         * @return attributes
         */
        Map<String, Object> attributes();
    }

    /**
     * Combined scope and spans information.
     */
    interface LogScopeSpans {
        /**
         * Returns the {@link io.helidon.telemetry.testing.tracing.JsonLogConverter.LogScope} portion
         * of the {@code LogScopeSpans} data.
         *
         * @return {@code LogScope}
         */
        LogScope logScope();

        /**
         * Returns the {@link io.helidon.telemetry.testing.tracing.JsonLogConverter.LogSpan} instances portion
         * of the {@code LogScopeSpans} data.
         *
         * @return {@code LogSpan} instances
         */
        List<? extends LogSpan> logSpans();
    }

    /**
     * Information about a "log scope" in the emitted span data.
     */
    interface LogScope {
        /**
         * Returns the name from the {@code LogScope}.
         *
         * @return name
         */
        String name();

        /**
         * Returns the attributes from the {@code LogScope}.
         *
         * @return attributes
         */
        Map<String, Object> attributes();
    }

    /**
     * Information describing a specific span.
     */
    interface LogSpan {
        /**
         * Returns the span's trace ID.
         *
         * @return trace ID
         */
        String traceId();

        /**
         * Returns the span's span ID.
         *
         * @return span ID
         */
        String spanId();

        /**
         * Returns the span's parent span ID or an empty string if there is no parent span.
         *
         * @return parent span ID
         */
        String parentSpanId();

        /**
         * Returns the span name.
         *
         * @return span name
         */
        String name();

        /**
         * Returns the span kind.
         *
         * @return span kind
         */
        int kind();

        /**
         * Returns the start time in nanoseconds of the span.
         *
         * @return span start time
         */
        long startTimeUnixNanos();

        /**
         * Returns the end time in nanoseconds of the span.
         *
         * @return span end time
         */
        long endTimeUnixNanos();

        /**
         * Returns attributes assigned to the span.
         *
         * @return span-level attributes
         */
        Map<String, Object> attributes();
    }
}
