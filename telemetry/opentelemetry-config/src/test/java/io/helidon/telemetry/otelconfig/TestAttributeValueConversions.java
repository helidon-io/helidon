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

package io.helidon.telemetry.otelconfig;

import java.util.Map;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import io.opentelemetry.api.common.AttributeKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

class TestAttributeValueConversions {

    static final Config config = Config.just(ConfigSources.create(
            """
                    telemetry:
                      service: "test-otel"
                      global: false
                      signals:
                        tracing:
                          attributes:
                            strings:
                              attr1: 12
                              attr5: "any old thing"
                              attr7: something
                            longs:
                              attr2: 12
                            doubles:
                              attr3: 24.5
                              attr6: 12
                            booleans:
                              attr4: true
                          sampler:
                            type: "always_off"
                          exporters:
                            - type: otlp
                              protocol: http/proto
                              name: my-oltp
                            - type: zipkin
                          processors:
                            - max-queue-size: 21
                              type: batch
                            - max-queue-size: 22
                              type: simple
                              exporters: ["my-oltp"]
                    """,
            MediaTypes.APPLICATION_YAML));

    static final OpenTelemetryTracingConfig tracingConfig = OpenTelemetryConfig.builder()
            .config(config.get("telemetry"))
            .build()
            .prototype()
            .tracingConfig()
            .get();

    static final Map<AttributeKey<?>, Object> attrs = tracingConfig.tracingBuilderInfo()
            .attributesBuilder()
            .build()
            .asMap();

    static Stream<Arguments> testConversions() {
        return Stream.of(Arguments.arguments("attr1", String.class, "12"),
                         Arguments.arguments("attr2", Long.class, 12L),
                         Arguments.arguments("attr3", Double.class, 24.5D),
                         Arguments.arguments("attr4", Boolean.class, true),
                         Arguments.arguments("attr5", String.class, "any old thing"),
                         Arguments.arguments("attr6", Double.class, 12D),
                         Arguments.arguments("attr7", String.class, "something"));
    }

    static AttributeKey<?> getKey(String key, Object expectedValue) {
        return switch (expectedValue) {
            case String ignore -> AttributeKey.stringKey(key);
            case Double ignore -> AttributeKey.doubleKey(key);
            case Boolean ignore -> AttributeKey.booleanKey(key);
            case Long ignore -> AttributeKey.longKey(key);
            default -> throw new IllegalStateException("Unexpected type: " + expectedValue.getClass());
        };
    }

    @ParameterizedTest
    @MethodSource
    void testConversions(String key, Class<?> expectedType, Object expectedValue) {
        var result = attrs.get(getKey(key, expectedValue));
        assertThat("Value type", result, instanceOf(expectedType));
        assertThat("Value ", result, is(equalTo(expectedValue)));

    }

}
