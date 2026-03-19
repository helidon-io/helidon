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

package io.helidon.declarative.codegen.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Generated;
import io.helidon.common.types.Annotation;
import io.helidon.service.registry.Service;
import io.helidon.validation.Validation;

import org.junit.jupiter.api.Test;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class ValidationCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Validation.Constraint.class,
            Annotation.class,
            Service.class,
            Prototype.class
    );

    @Test
    void testFieldValidation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;
                        
                        import io.helidon.validation.Validation;
                        
                        @Validation.Validated
                        public class Foo {
                            @Validation.Integer.Min(1)
                            Integer id;
                        }
                        """)
                .build()
                .compile();

        assertThat(result.success(), is(true));
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));

        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, matches("""
                                            //...
                                            class Foo__Validated implements TypeValidator<Foo> {
                                            //...
                                                        case "id" -> checkId(ctx, (Integer) value);
                                            //...
                                            }
                                            """));
        String diags = String.join("\n", result.diagnostics());
        assertThat(diags, not(containsString("warning:")));
    }

    @Test
    void testPrivateFieldValidation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;
                        
                        import io.helidon.validation.Validation;
                        
                        @Validation.Validated
                        public class Foo {
                            @Validation.Integer.Min(1)
                            private Integer id;
                        }
                        """)
                .build()
                .compile();

        assertThat("Build should fail, as we do not support constraints on private fields", result.success(), is(false));
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validator.java should not be generated", Files.exists(validator), is(false));

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("private Integer id"));
        assertThat(diagnostics, containsString("Only non-private fields and getter methods are supported"));
    }
}
