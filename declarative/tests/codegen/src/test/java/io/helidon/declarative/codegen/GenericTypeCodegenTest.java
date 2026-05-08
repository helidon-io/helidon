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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.GenericType;
import io.helidon.common.types.Annotation;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class GenericTypeCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            GenericType.class,
            Annotation.class,
            Dependency.class,
            Prototype.class,
            Service.class,
            ServiceDescriptor.class
    );

    @Test
    void testGeneratedDescriptorsUseTypedGenericTypeConstants() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addOption("-Xlint:rawtypes")
                .addOption("-Werror")
                .printDiagnostics(false)
                .addSource("RedFetcher.java", """
                        package com.example;

                        import java.util.List;

                        import io.helidon.service.registry.Service;

                        @Service.Contract
                        interface RedQueryBuilder<T> {
                        }

                        @Service.Singleton
                        class RedQueryBuilderImpl implements RedQueryBuilder<String> {
                        }

                        @Service.Singleton
                        class RedFetcher {
                            @Service.Inject
                            RedFetcher(List<RedQueryBuilder<String>> builders, RedQueryBuilder<String> builder) {
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("found raw type: io.helidon.common.GenericType")));
        assertThat(diagnostics, not(containsString("warning:")));

        var descriptor = result.sourceOutput().resolve("com/example/RedFetcher__ServiceDescriptor.java");
        assertThat(Files.exists(descriptor), is(true));

        var content = Files.readString(descriptor, StandardCharsets.UTF_8);
        assertThat(content, containsString("private static final GenericType<List<RedQueryBuilder<String>>> GTYPE"));
        assertThat(content, containsString("private static final GenericType<RedQueryBuilder<String>> GTYPE_1"));
    }
}
