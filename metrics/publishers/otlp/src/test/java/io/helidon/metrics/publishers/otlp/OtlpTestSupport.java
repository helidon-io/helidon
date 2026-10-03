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

package io.helidon.metrics.publishers.otlp;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.helidon.json.JsonArray;
import io.helidon.json.JsonObject;
import io.helidon.json.JsonParser;
import io.helidon.json.JsonValue;
import io.helidon.json.JsonValueType;
import io.helidon.json.binding.JsonBinding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

final class OtlpTestSupport {
    private static final JsonBinding JSON_BINDING = JsonBinding.create();

    private OtlpTestSupport() {
    }

    static JsonObject collect(OtlpEncoder encoder) {
        byte[] bytes = JSON_BINDING.serializeToBytes(encoder.collect());
        return JsonParser.create(bytes).readJsonObject();
    }

    static List<JsonObject> metrics(JsonObject request) {
        return objects(request, "resourceMetrics").stream()
                .flatMap(resource -> objects(resource, "scopeMetrics").stream())
                .flatMap(scope -> objects(scope, "metrics").stream())
                .toList();
    }

    static JsonObject metric(JsonObject request, String name) {
        return metrics(request).stream()
                .filter(metric -> metric.stringValue("name").orElseThrow().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing metric " + name + " in " + request));
    }

    static List<JsonObject> dataPoints(JsonObject metric, String kind) {
        JsonObject data = metric.objectValue(kind).orElseThrow();
        return objects(data, "dataPoints");
    }

    static Map<String, String> attributes(JsonObject owner) {
        return objects(owner, "attributes").stream()
                .collect(Collectors.toMap(attribute -> attribute.stringValue("key").orElseThrow(),
                                          attribute -> attribute.objectValue("value").orElseThrow()
                                                  .stringValue("stringValue").orElseThrow()));
    }

    static List<JsonObject> objects(JsonObject owner, String name) {
        return owner.arrayValue(name).orElse(JsonArray.empty()).values().stream()
                .map(JsonValue::asObject)
                .toList();
    }

    static long longValue(JsonObject owner, String name) {
        JsonValue value = owner.value(name).orElseThrow(() -> new AssertionError("Missing " + name + " in " + owner));
        assertThat("OTLP 64-bit field " + name + " is a JSON string", value.type(), is(JsonValueType.STRING));
        return Long.parseLong(value.asString().value());
    }

    static List<Long> longValues(JsonObject owner, String name) {
        return owner.arrayValue(name).orElse(JsonArray.empty()).values().stream()
                .map(value -> {
                    assertThat("OTLP 64-bit array " + name + " contains JSON strings", value.type(), is(JsonValueType.STRING));
                    return Long.parseLong(value.asString().value());
                })
                .toList();
    }

    static List<Double> doubleValues(JsonObject owner, String name) {
        return owner.arrayValue(name).orElse(JsonArray.empty()).values().stream()
                .map(value -> value.asNumber().doubleValue())
                .toList();
    }
}
