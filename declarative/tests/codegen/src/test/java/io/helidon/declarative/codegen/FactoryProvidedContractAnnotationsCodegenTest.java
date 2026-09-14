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

package io.helidon.declarative.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.faulttolerance.Ft;
import io.helidon.faulttolerance.Retry;
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.Tracing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class FactoryProvidedContractAnnotationsCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            GenericType.class,
            Annotation.class,
            Prototype.class,
            Service.class,
            Ft.class,
            Retry.class,
            Metrics.class,
            Meter.class,
            Gauge.class,
            MeterRegistry.class,
            Tracing.class,
            Span.class,
            Tracer.class
    );

    @Test
    void testSupplierProvidedFtContractDoesNotGenerateInterceptor() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ProvidedFtApi.java", """
                        package com.example;

                        import io.helidon.faulttolerance.Ft;
                        import io.helidon.service.registry.Service;

                        @Service.Contract
                        interface ProvidedFtApi {
                            @Ft.Retry(calls = 2)
                            String value();
                        }
                        """)
                .addSource("ProvidedFtApiSupplier.java", """
                        package com.example;

                        import java.util.function.Supplier;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class ProvidedFtApiSupplier implements Supplier<ProvidedFtApi> {
                            @Override
                            public ProvidedFtApi get() {
                                return () -> "value";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("com/example/ProvidedFtApiSupplier_value__Retry.java")),
                   is(false));
    }

    @Test
    void testSupplierProvidedMetricsContractDoesNotGenerateRegistrar() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ProvidedMetricsApi.java", """
                        package com.example;

                        import io.helidon.metrics.api.Meter;
                        import io.helidon.metrics.api.Metrics;
                        import io.helidon.service.registry.Service;

                        @Service.Contract
                        interface ProvidedMetricsApi {
                            @Metrics.Gauge(unit = Meter.BaseUnits.BYTES)
                            long size();
                        }
                        """)
                .addSource("ProvidedMetricsApiSupplier.java", """
                        package com.example;

                        import java.util.function.Supplier;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class ProvidedMetricsApiSupplier implements Supplier<ProvidedMetricsApi> {
                            @Override
                            public ProvidedMetricsApi get() {
                                return () -> 42L;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("com/example/ProvidedMetricsApiSupplier__GaugeRegistrar.java")),
                   is(false));
    }

    @Test
    void testSupplierProvidedTracingContractDoesNotGenerateInterceptor() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ProvidedTracingApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        interface ProvidedTracingApi {
                            @Tracing.Traced
                            String traced();
                        }
                        """)
                .addSource("ProvidedTracingApiSupplier.java", """
                        package com.example;

                        import java.util.function.Supplier;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class ProvidedTracingApiSupplier implements Supplier<ProvidedTracingApi> {
                            @Override
                            public ProvidedTracingApi get() {
                                return () -> "traced";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(Files.exists(result.sourceOutput().resolve("com/example/ProvidedTracingApiSupplier__TracedInterceptor.java")),
                   is(false));
    }
}
