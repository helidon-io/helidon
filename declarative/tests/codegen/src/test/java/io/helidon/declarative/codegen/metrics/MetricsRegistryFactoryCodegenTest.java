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

package io.helidon.declarative.codegen.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class MetricsRegistryFactoryCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            GenericType.class,
            Annotation.class,
            Prototype.class,
            Service.class,
            Metrics.class,
            Meter.class,
            Gauge.class,
            MeterRegistry.class
    );

    @Test
    void testGeneratedMetricsUseRegistryOwnedFactory() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("MetricsService.java", """
                        package com.example;

                        import io.helidon.metrics.api.Meter;
                        import io.helidon.metrics.api.Metrics;
                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class MetricsService {
                            @Metrics.Counted
                            String counted() {
                                return "counted";
                            }

                            @Metrics.Timed
                            String timed() {
                                return "timed";
                            }

                            @Metrics.Gauge(unit = Meter.BaseUnits.BYTES)
                            long gauge() {
                                return 42L;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        String counted = Files.readString(
                result.sourceOutput().resolve("com/example/MetricsService__CountedInterceptor.java"));
        String timed = Files.readString(
                result.sourceOutput().resolve("com/example/MetricsService__TimedInterceptor.java"));
        String gauge = Files.readString(
                result.sourceOutput().resolve("com/example/MetricsService__GaugeRegistrar.java"));

        assertThat(counted, containsString("var metricsFactory = meterRegistry.metricsFactory();"));
        assertThat(counted, not(containsString("MetricsFactory metricsFactory")));
        assertThat(timed, containsString("var metricsFactory = meterRegistry.metricsFactory();"));
        assertThat(timed, not(containsString("MetricsFactory metricsFactory")));
        assertThat(gauge, containsString("var metricsFactory = meters.metricsFactory();"));
        assertThat(gauge, not(containsString("metricsFactorySupplier")));
    }
}
