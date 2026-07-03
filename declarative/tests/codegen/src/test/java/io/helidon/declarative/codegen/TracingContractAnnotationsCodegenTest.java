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
import io.helidon.service.registry.Service;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.Tracing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class TracingContractAnnotationsCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            GenericType.class,
            Annotation.class,
            Prototype.class,
            Service.class,
            Tracing.class,
            Span.class,
            Tracer.class
    );

    @Test
    void testMethodTypeVariableNamesDoNotAffectMatching() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("GenericMethodContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced
                        interface GenericMethodContract {
                            <T> String echo(T value);
                        }
                        """)
                .addSource("GenericMethodService.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class GenericMethodService implements GenericMethodContract {
                            @Override
                            public <U> String echo(U value) {
                                return value.toString();
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        assertThat(diagnostics,
                   Files.exists(result.sourceOutput().resolve("com/example/GenericMethodService__TracedInterceptor.java")),
                   is(true));
    }

    @Test
    void testMethodTypeVariableBoundsResolveContractTypeArguments() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("BoundedMethodContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced
                        interface BoundedMethodContract<X> {
                            <T extends X> T direct(T value);

                            default <T extends Comparable<X>> T nested(T value) {
                                return value;
                            }
                        }
                        """)
                .addSource("BoundedMethodService.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class BoundedMethodService implements BoundedMethodContract<Number> {
                            @Override
                            public <U extends Number> U direct(U value) {
                                return value;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = Files.readString(
                result.sourceOutput().resolve("com/example/BoundedMethodService__Intercepted.java"));
        assertThat(diagnostics, generated, containsString("<T extends Comparable<Number>> T nested(T value)"));
    }

    @Test
    void testMoreSpecificContractTypeAnnotationWins() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("BaseTracingContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced("base-span")
                        interface BaseTracingContract {
                            String value();
                        }
                        """)
                .addSource("SpecificTracingContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced("specific-span")
                        interface SpecificTracingContract extends BaseTracingContract {
                            @Override
                            String value();
                        }
                        """)
                .addSource("SpecificTracingService.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class SpecificTracingService implements SpecificTracingContract {
                            @Override
                            public String value() {
                                return "value";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = Files.readString(
                result.sourceOutput().resolve("com/example/SpecificTracingService__TracedInterceptor.java"));
        assertThat(generated, containsString("specific-span"));
    }

    @Test
    void testUnrelatedContractTypeAnnotationsAreRejected() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("FirstTracingContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced("first-span")
                        interface FirstTracingContract {
                            String value();
                        }
                        """)
                .addSource("SecondTracingContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        @Tracing.Traced("second-span")
                        interface SecondTracingContract {
                            String value();
                        }
                        """)
                .addSource("AmbiguousTracingService.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class AmbiguousTracingService implements FirstTracingContract, SecondTracingContract {
                            @Override
                            public String value() {
                                return "value";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics, containsString("Ambiguous tracing type annotations"));
    }

    @Test
    void testDefaultParameterTagUsesContractParameterName() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("TaggedContract.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        interface TaggedContract {
                            String tagged(@Tracing.ParamTag int contractId);
                        }
                        """)
                .addSource("TaggedService.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Singleton
                        @Tracing.Traced
                        class TaggedService implements TaggedContract {
                            @Override
                            public String tagged(int id) {
                                return Integer.toString(id);
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));
        String generated = Files.readString(
                result.sourceOutput().resolve("com/example/TaggedService__TracedInterceptor.java"));
        assertThat(generated, containsString(".tag(\"contractId\", contractId)"));
    }

    @Test
    void testTypeTracedFactoryExcludesProvidedMethods() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("ProvidedApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.Contract
                        interface ProvidedApi {
                            String providedOnly();
                        }
                        """)
                .addSource("OrdinaryApi.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Contract
                        interface OrdinaryApi {
                            @Tracing.Traced
                            String ordinary();
                        }
                        """)
                .addSource("ProvidedApiSupplier.java", """
                        package com.example;

                        import java.util.function.Supplier;

                        import io.helidon.service.registry.Service;
                        import io.helidon.tracing.Tracing;

                        @Service.Singleton
                        @Tracing.Traced
                        class ProvidedApiSupplier implements Supplier<ProvidedApi>, OrdinaryApi {
                            @Override
                            public ProvidedApi get() {
                                return () -> "provided";
                            }

                            @Override
                            public String ordinary() {
                                return "ordinary";
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(true));

        boolean providedMethodInterceptor = false;
        try (var files = Files.list(result.sourceOutput().resolve("com/example"))) {
            for (var file : files.filter(it -> it.getFileName().toString().contains("__TracedInterceptor")).toList()) {
                if (Files.readString(file).contains("ProvidedApiSupplier.providedOnly()")) {
                    providedMethodInterceptor = true;
                    break;
                }
            }
        }
        assertThat("Factory-provided methods must not be traced", providedMethodInterceptor, is(false));
    }
}
