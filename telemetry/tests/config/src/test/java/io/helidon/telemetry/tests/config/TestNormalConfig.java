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

package io.helidon.telemetry.tests.config;

import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.Services;
import io.helidon.telemetry.api.Telemetry;
import io.helidon.tracing.Tracer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

class TestNormalConfig {

    @Test
    void testTelemetryWithTracingSignal() {

        Config config = Config.just(ConfigSources.create(
                """
                        telemetry:
                          service: "test-otel"
                          global: false
                          signals:
                            tracing:
                              sampler:
                                type: "always_on"
                              exporters:
                                - type: otlp
                                  protocol: http/proto
                                  name: my-oltp
                                - type: zipkin
                              processors:
                                - max-queue-size: 21
                                  type: batch
                        """,
                MediaTypes.APPLICATION_YAML));

        Services.set(Config.class, config);
        Tracer tracer = Services.get(Tracer.class);
        assertThat("Tracer", tracer, notNullValue());
        assertThat("Tracer class", tracer.getClass().getName(), containsString("OpenTelemetryTracer"));

        // Programmatic construction is not yet ready.
//        Telemetry t = Telemetry.builder()
//                .service("test-otel")
//                .propagations(List.of("tracecontext"))
//                .build();
//
//        Tracer tr = t.signal(Tracer.class)
//                .get().get("NewOne");



    }

}
