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

import io.helidon.common.GenericType;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.Json;
import io.helidon.json.binding.JsonSerializer;

// OTLP JSON uses decimal strings for 64-bit integers and omits absent message and oneof fields.
@Json.Entity
record OtlpRequest(List<ResourceMetrics> resourceMetrics) {
    static final OtlpRequest EMPTY = new OtlpRequest(List.of());

    OtlpRequest {
        resourceMetrics = List.copyOf(resourceMetrics);
    }

    @Json.Entity
    record ResourceMetrics(Resource resource, List<ScopeMetrics> scopeMetrics) {
        ResourceMetrics {
            scopeMetrics = List.copyOf(scopeMetrics);
        }
    }

    @Json.Entity
    record Resource(List<KeyValue> attributes) {
        Resource {
            attributes = List.copyOf(attributes);
        }
    }

    @Json.Entity
    record ScopeMetrics(InstrumentationScope scope, List<Metric> metrics) {
        ScopeMetrics {
            metrics = List.copyOf(metrics);
        }
    }

    @Json.Entity
    record InstrumentationScope(String name) {
    }

    @Json.Entity
    record KeyValue(String key, AnyValue value) {
    }

    @Json.Entity
    record AnyValue(String stringValue) {
    }

    @Json.Entity
    record Metric(String name, String description, String unit, Sum sum, Gauge gauge, Histogram histogram) {
    }

    @Json.Entity
    record Sum(List<NumberDataPoint> dataPoints, int aggregationTemporality, boolean isMonotonic) {
        Sum {
            dataPoints = List.copyOf(dataPoints);
        }
    }

    @Json.Entity
    record Gauge(List<NumberDataPoint> dataPoints) {
        Gauge {
            dataPoints = List.copyOf(dataPoints);
        }
    }

    @Json.Entity
    record Histogram(List<HistogramDataPoint> dataPoints, int aggregationTemporality) {
        Histogram {
            dataPoints = List.copyOf(dataPoints);
        }
    }

    @Json.Entity
    record NumberDataPoint(List<KeyValue> attributes,
                           String startTimeUnixNano,
                           String timeUnixNano,
                           String asInt,
                           @Json.Serializer(DoubleSerializer.class) Double asDouble) {
        NumberDataPoint {
            attributes = List.copyOf(attributes);
        }
    }

    @Json.Entity
    record HistogramDataPoint(List<KeyValue> attributes,
                              String startTimeUnixNano,
                              String timeUnixNano,
                              String count,
                              @Json.Serializer(DoubleSerializer.class) Double sum,
                              @Json.Serializer(DoubleSerializer.class) Double max,
                              List<Double> explicitBounds,
                              List<String> bucketCounts) {
        HistogramDataPoint {
            attributes = List.copyOf(attributes);
            explicitBounds = List.copyOf(explicitBounds);
            bucketCounts = List.copyOf(bucketCounts);
        }
    }

    static final class DoubleSerializer implements JsonSerializer<Double> {
        private static final GenericType<Double> TYPE = GenericType.create(Double.class);

        @Override
        public void serialize(JsonGenerator generator, Double instance, boolean writeNulls) {
            if (Double.doubleToRawLongBits(instance) == Double.doubleToRawLongBits(-0.0)) {
                // ProtoJSON accepts quoted doubles. Preserve the sign that the UTF-8 generator otherwise normalizes.
                generator.write("-0.0");
            } else {
                generator.write(instance.doubleValue());
            }
        }

        @Override
        public GenericType<Double> type() {
            return TYPE;
        }
    }
}
