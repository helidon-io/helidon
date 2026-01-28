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
package io.helidon.telemetry.testing.tracing;

import java.io.StringReader;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.helidon.common.testing.junit5.MatcherWithRetry;

import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import static org.hamcrest.Matchers.hasSize;

class JsonLogConverterImpl implements JsonLogConverter {

    /**
     * The format emits all attribute values as strings, but each attribute value has a key indicating its datatype.
     */
    private static final Map<String, Function<String, Object>> DATA_TYPE_MAPPERS = Map.of("intValue", Integer::parseInt,
                                                                                     "longValue", Long::parseLong,
                                                                                     "floatValue", Float::parseFloat,
                                                                                     "doubleValue", Double::parseDouble,
                                                                                     "stringValue", String::toString);

    private final Logger logger;
    private final TestLogHandler testLogHandler;

    JsonLogConverterImpl() {

        // The OTLP JSON span exporter places JSON blocks for each span in the logger.
        // Attach our handler to that logger so we can check the exported data easily.

        this(Logger.getLogger(OtlpJsonLoggingSpanExporter.class.getName()));
    }

    JsonLogConverterImpl(Logger testLogger) {
        logger = testLogger;
        testLogHandler = new TestLogHandler();
        logger.addHandler(testLogHandler);
    }

    @Override
    public void close() throws Exception {
        logger.removeHandler(testLogHandler);
    }

    @Override
    public List<JsonLogConverter.LogResourceScopeSpans> resourceSpans(int expectedCount) {

        // The logger might take a moment to flush its output to our handler, so retry for a bit until the span count reaches
        // the expected value.

        return MatcherWithRetry.assertThatWithRetry("Span infos",
                                                    () -> testLogHandler.resourceSpans().stream()
                                                            .map(this::fromJson)
                                                            .filter(resourceScopeSpans ->
                                                                            !resourceScopeSpans.scopeSpans().isEmpty())
                                                            .toList(),
                                                    hasSize(expectedCount));
    }

    /**
     * Converts a block of JSON corresponding to a single span into a
     * {@link JsonLogConverter.LogResourceScopeSpans}.
     *
     * @param jsonText JSON text containing the information about the span
     * @return {@code SpanInfo} describing the span
     */
    private JsonLogConverter.LogResourceScopeSpans fromJson(String jsonText) {
        JsonReader jsonReader = Json.createReader(new StringReader(jsonText));
        JsonObject topLevelJsonObject = jsonReader.readObject();

        LogResource resource = resource(topLevelJsonObject);
        List<LogScopeSpans> scopeSpans = scopeSpansFromTopLevelJson(topLevelJsonObject);

        return new LogResourceScopeSpans(resource, scopeSpans);
    }

    /**
     * Creates a {@link io.helidon.telemetry.testing.tracing.JsonLogConverterImpl.LogResource} from the provided JSON object if
     * it contains a {@code resource} subnode and
     * an "empty" {@code LogResource} otherwise.
     *
     * @param jsonObject JSON object which might/should contain a {@code resource} subnode
     * @return {@code LogResource} corresponding to the
     */
    private LogResource resource(JsonObject jsonObject) {
        return new LogResource(jsonObject.containsKey("resource")
                                       ? attributes(jsonObject.getJsonObject("resource"))
                                       : Map.of());
    }

    /**
     * Creates a {@code Map} representing the key/value pairs in the {@code attributes} subnode within the provided JSON object;
     * returning an empty map if the JSON object contains no {@code attributes} subnode.
     *
     * @param jsonObject JSON object possibly containing an {@code attributes} subnode
     * @return Map containing key/value pairs for the attributes
     */
    private Map<String, Object> attributes(JsonObject jsonObject) {
        return jsonObject.containsKey("attributes")
                ? jsonObject.getJsonArray("attributes")
                .stream()
                .map(JsonValue::asJsonObject)
                .map(this::attribute)
                .collect(HashMap::new,
                         (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                         HashMap::putAll)
                : Map.of();
    }

    /**
     * Creates a list of {@link io.helidon.telemetry.testing.tracing.JsonLogConverterImpl.LogScopeSpans} objects
     * using the {@code scopeSpans} subnode in the provided top-level JSON object, returning an empty list if there is
     * no such subnode.
     *
     * @param topLevelJsonObject top-level JSON object possibly containing a {@code scopeSpans} subnode
     * @return list of {@code LogScopeSpans} objects
     */
    private List<LogScopeSpans> scopeSpansFromTopLevelJson(JsonObject topLevelJsonObject) {

        return topLevelJsonObject.containsKey("scopeSpans")
                ? topLevelJsonObject.getJsonArray("scopeSpans")
                .stream()
                .map(JsonValue::asJsonObject)
                .map(this::scopeSpans)
                .toList()
                : List.of();
    }

    /**
     * Creates a {@link io.helidon.telemetry.testing.tracing.JsonLogConverterImpl.LogScopeSpans} instance from the
     * provided JSON object corresponding to a scope and possibly multiple spans.
     *
     * @param scopeAndSpansJsonObject JSON object containing the scope and possibly spans
     * @return {@code LogScopeSpans} representation of the scope and spans
     */
    private LogScopeSpans scopeSpans(JsonObject scopeAndSpansJsonObject) {
        return scopeAndSpansJsonObject.containsKey("scope")
                ? new LogScopeSpans(logScope(scopeAndSpansJsonObject.getJsonObject("scope")),
                                    logSpans(scopeAndSpansJsonObject.getJsonArray("spans")))
                : new LogScopeSpans(new LogScope("empty", Map.of()),
                                    List.of());
    }

    private LogScope logScope(JsonObject scopeJsonObject) {
        return new LogScope(scopeJsonObject.getString("name", "no-name"),
                            attributes(scopeJsonObject));
    }

    private List<LogSpan> logSpans(JsonArray spansJsonArray) {
        return (spansJsonArray == null || spansJsonArray.isEmpty())
                ? List.of()
                : spansJsonArray.stream()
                        .map(JsonValue::asJsonObject)
                        .map(this::span)
                        .toList();
    }

    private LogSpan span(JsonObject spanJsonObject) {

        String traceId = spanJsonObject.getString("traceId");
        String spanId = spanJsonObject.getString("spanId");

        String parentSpanId = spanJsonObject.containsKey("parentSpanId")
                ? spanJsonObject.getString("parentSpanId")
                : "";

        String name = spanJsonObject.getString("name");
        int kind = spanJsonObject.getInt("kind");
        long startTimeUnixNano = Long.parseLong(spanJsonObject.getJsonString("startTimeUnixNano").getString());
        long endTimeUnixNano = Long.parseLong(spanJsonObject.getJsonString("endTimeUnixNano").getString());

        return new LogSpan(traceId,
                           spanId,
                           parentSpanId,
                           name,
                           kind,
                           startTimeUnixNano,
                           endTimeUnixNano,
                           attributes(spanJsonObject));
    }

    /**
     * Converts a JSON object for a single attribute into a name/value pair, where the value
     * is correctly typed based on the JSON.
     * <p>
     * The emitted JSON for an attribute looks like this:
     * {@snippet lang = json:
     *       {
     *         "key": "telemetry.sdk.name",
     *         "value": {
     *           "stringValue": "opentelemetry"
     *         }
     *       }
     *}
     * This method converts that sort of JSON into a name/value pair with the value typed according to the key within the
     * {@code value} block.
     *
     * @param jsonAttributeElement the JSON object representing the attribute
     * @return name/value pair
     */
    private Map.Entry<String, ?> attribute(JsonObject jsonAttributeElement) {
        String key = jsonAttributeElement.getString("key");
        JsonObject value = jsonAttributeElement.get("value").asJsonObject();

        /*
        Normally, every attribute value, regardless of type, is represented in the JSON as a string in double quotes.
        Below, we use {@code value.getString(k)} to strip off the double quotes so the various datatype parsers can work on
        the enclosed string.

        An attribute with an empty value (an empty string) will not have any value keyset in the JSON which OpenTelemetry
        generates, so for those return a map entry with an empty string value.
         */
        return value.keySet()
                .stream()
                .map(k -> new AbstractMap.SimpleEntry<>(key,
                                                        DATA_TYPE_MAPPERS.get(k)
                                                                .apply(value.getString(k))))
                .findFirst()
                .orElseGet(() -> new AbstractMap.SimpleEntry<>(key, ""));

    }

    /**
     * Top-level data for an emitted span, typically containing a resource with attribute settings and a "scope-span" containing
     * one span.
     *
     * @param resource {@link JsonLogConverter.LogResource} (with attributes)
     * @param scopeSpans {@link JsonLogConverter.LogScopeSpans} (with typically one
     *                                                                                                     span)
     */
    record LogResourceScopeSpans(LogResource resource, List<LogScopeSpans> scopeSpans)
            implements JsonLogConverter.LogResourceScopeSpans {
    }

    record LogResource(Map<String, Object> attributes) implements JsonLogConverter.LogResource {
    }

    /**
     * Encapsulation of trace scope information and (typically) one span.
     *
     * @param logScope {@link JsonLogConverter.LogScope} relevant to the span(s)
     * @param logSpans {@link JsonLogConverter.LogSpan} span data
     */
    record LogScopeSpans(LogScope logScope, List<LogSpan> logSpans) implements JsonLogConverter.LogScopeSpans {
    }

    record LogScope(String name, Map<String, Object> attributes) implements JsonLogConverter.LogScope {
    }

    /**
     * Information describing a specific span.
     *
     * @param traceId trace ID
     * @param spanId span ID
     * @param parentSpanId span ID of the parent span (empty string if no parent)
     * @param name name given to the span
     * @param kind kind of span (e.g., client, server, internal)
     * @param startTimeUnixNanos when the span started
     * @param endTimeUnixNanos when the span ended
     * @param attributes attributes assigned to the span
     */
    record LogSpan(String traceId, String spanId, String parentSpanId, String name, int kind, long startTimeUnixNanos,
                   long endTimeUnixNanos,
                   Map<String, Object> attributes) implements JsonLogConverter.LogSpan {
    }

    static class TestLogHandler extends Handler {

        // For testing.
        static TestLogHandler create() {
            return new TestLogHandler();
        }


        private final List<String> resourceSpans = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(LogRecord record) {
            resourceSpans.add(record.getMessage());
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }

        List<String> resourceSpans() {
            return List.copyOf(resourceSpans);
        }

    }

}
