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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.nullValue;

class TestAttributeValueConversions {

    static final Config config = Config.just(ConfigSources.create(
            """
                    telemetry:
                      service: "test-otel"
                      global: false
                      signals:
                        tracing:
                          attributes:
                            attr1: \\"12\\"
                            attr2: 12
                            attr3: 24.5
                            attr4: true
                            attr5: anything
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

    @ParameterizedTest
    @MethodSource
    void testConversions(Config config, Class<?> expectedType, Object expectedValue) {
        var result = OpenTelemetryTracingConfigSupport.CustomMethods.createAttributes(config);
        assertThat("Value text " + config.asString().get(), result, is(equalTo(expectedValue)));

    }

    static Stream<Arguments> testConversions() {
        Config attrs = config.get("telemetry.signals.tracing.attributes");
        return Stream.of(Arguments.arguments(attrs.get("attr1"), String.class, "12"),
                         Arguments.arguments(attrs.get("attr2"), Long.class, 12L),
                         Arguments.arguments(attrs.get("attr3"), Double.class, 24.5D),
                         Arguments.arguments(attrs.get("attr4"), Boolean.class, true),
                         Arguments.arguments(attrs.get("attr5"), String.class, "anything"));
    }


}
