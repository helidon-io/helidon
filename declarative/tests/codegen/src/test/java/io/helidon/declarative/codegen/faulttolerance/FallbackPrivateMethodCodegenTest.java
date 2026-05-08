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

package io.helidon.declarative.codegen.faulttolerance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.Annotation;
import io.helidon.faulttolerance.ErrorChecker;
import io.helidon.faulttolerance.Ft;
import io.helidon.faulttolerance.FtSupport;
import io.helidon.service.registry.Dependency;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceDescriptor;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class FallbackPrivateMethodCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Annotation.class,
            Dependency.class,
            ErrorChecker.class,
            Ft.class,
            FtSupport.class,
            Generated.class,
            Prototype.class,
            Service.class,
            ServiceDescriptor.class
    );

    @Test
    void privateFallbackMethodIsRejectedBeforeGeneratedCompileFails() throws IOException {
        Path workDirRoot = Path.of("target/test-compiler");
        Files.createDirectories(workDirRoot);
        Path workDir = Files.createTempDirectory(workDirRoot, "faulttolerance-private-fallback-");

        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .workDir(workDir)
                .addSource("FallbackService.java", """
                        package com.example;

                        import io.helidon.faulttolerance.Ft;
                        import io.helidon.service.registry.Service;

                        @Service.Singleton
                        class FallbackService {
                            @Ft.Fallback("privateFallback")
                            String hello(String name) {
                                throw new IllegalStateException("boom");
                            }

                            private String privateFallback(String name) {
                                return "fallback " + name;
                            }
                        }
                        """)
                .addSource("Main.java", """
                        package com.example;

                        import io.helidon.service.registry.Service;

                        @Service.GenerateBinding
                        class Main {
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, result.success(), is(false));
        assertThat(diagnostics,
                   containsString("Fallback method privateFallback(java.lang.String) on com.example.FallbackService"
                                          + " must not be private"));
        assertThat(diagnostics, containsString("generated interceptors cannot call private methods"));
        assertThat(diagnostics, not(containsString("has private access")));

        var generatedFallback = result.sourceOutput().resolve("com/example/FallbackService_hello__Fallback.java");
        assertThat(Files.exists(generatedFallback), is(false));
    }
}
