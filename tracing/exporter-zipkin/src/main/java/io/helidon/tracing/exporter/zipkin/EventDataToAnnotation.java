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

package io.helidon.tracing.exporter.zipkin;

import java.util.List;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.common.ValueType;
import io.opentelemetry.sdk.trace.data.EventData;

import static java.util.stream.Collectors.joining;

/**
 * Converts an EventData instance to a String representation of that data, with attributes converted
 * to JSON.
 *
 * <p>See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/sdk_exporters/zipkin.md#events">the
 * zipkin exporter spec</a> for details.
 */
final class EventDataToAnnotation {

    private EventDataToAnnotation() {
    }

    static String apply(EventData eventData) {
        String name = eventData.getName();
        String value = toJson(eventData.getAttributes());
        return "\"" + name + "\":" + value;
    }

    private static String toJson(Attributes attributes) {
        return attributes.asMap().entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + toValue(entry.getValue()))
                .collect(joining(",", "{", "}"));
    }

    private static String toValue(Object o) {
        if (o instanceof String) {
            return "\"" + o + "\"";
        }
        if (o instanceof List) {
            return ((List<?>) o)
                    .stream().map(EventDataToAnnotation::toValue).collect(joining(",", "[", "]"));
        }
        if (o instanceof Value) {
            return toJsonValue((Value<?>) o);
        }
        return String.valueOf(o);
    }

    // note: simple types (STRING, BOOLEAN, LONG, DOUBLE) won't actually come here
    // but handling here for completeness
    @SuppressWarnings("checkstyle:MissingSwitchDefault") // Exhaustive enum switch.
    private static String toJsonValue(Value<?> value) {
        ValueType type = value.getType();
        return switch (type) {
            case STRING, BYTES -> "\"" + value.asString() + "\"";
            case EMPTY -> "null";
            case ARRAY, KEY_VALUE_LIST, BOOLEAN, LONG, DOUBLE -> value.asString();
        };
    }
}
