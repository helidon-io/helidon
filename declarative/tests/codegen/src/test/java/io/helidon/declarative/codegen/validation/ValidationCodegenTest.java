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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

class ValidationCodegenTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Validation.Constraint.class,
            Annotation.class,
            Service.class,
            Prototype.class
    );

    @Test
    void testSupportedClassElements() throws IOException {
        var result = compile("""
                package com.example;

                import io.helidon.validation.Validation;

                @Validation.Validated
                public class Foo {
                    @Validation.Integer.Min(1)
                    Integer id;

                    @Validation.Integer.Min(1)
                    public Integer getAge() {
                        return 42;
                    }

                    @Validation.Integer.Min(1)
                    Integer size() {
                        return 1;
                    }
                }
                """);

        assertGeneratedAndContainsCases(result, "Foo", "id", "age", "size");
    }

    @Test
    void testSupportedRecordElements() throws IOException {
        var result = compile("""
                package com.example;

                import io.helidon.validation.Validation;

                @Validation.Validated
                public record Foo(@Validation.Integer.Min(1) Integer id,
                                  @Validation.Integer.Min(1) Integer age) {
                }
                """);

        assertGeneratedAndContainsCases(result, "Foo", "id", "age");
    }

    @Test
    void testSupportedInterfaceElements() throws IOException {
        var result = compile("""
                package com.example;

                import io.helidon.validation.Validation;

                @Validation.Validated
                public interface Foo {
                    @Validation.Integer.Min(1)
                    Integer getId();

                    @Validation.Integer.Min(1)
                    Integer size();
                }
                """);

        assertGeneratedAndContainsCases(result, "Foo", "id", "size");
    }

    @Test
    void testUnsupportedClassElements() {
        for (UnsupportedCase unsupportedCase : unsupportedClassElements()) {
            var result = compile(unsupportedCase.source());

            assertCompilationFails(result,
                                   "Only non-private fields and getter methods are supported",
                                   unsupportedCase.expectedElement());
            var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
            assertThat("The Foo__Validated.java should not be generated (" + unsupportedCase.name() + ")",
                       Files.exists(validator),
                       is(false));
        }
    }

    @Test
    void testUnsupportedValidatedTypeKind() {
        var result = compile("""
                package com.example;

                import io.helidon.validation.Validation;

                @Validation.Validated
                enum Foo {
                    INSTANCE
                }
                """);

        assertCompilationFails(result, "Only record, class, or interface are currently supported");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated", Files.exists(validator), is(false));
    }

    private static List<UnsupportedCase> unsupportedClassElements() {
        return List.of(
                new UnsupportedCase("private field",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Integer.Min(1)
                                         private Integer id;
                                     }
                                     """,
                                    "private Integer id"),
                new UnsupportedCase("static field",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Integer.Min(1)
                                         static Integer id;
                                     }
                                     """,
                                    "static Integer id"),
                new UnsupportedCase("private method",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Valid
                                         private Integer id() {
                                             return 1;
                                         }
                                     }
                                     """,
                                    "private Integer id()"),
                new UnsupportedCase("static method",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Valid
                                         static Integer id() {
                                             return 1;
                                         }
                                     }
                                     """,
                                    "static Integer id()"),
                new UnsupportedCase("method with arguments",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Valid
                                         Integer id(Integer value) {
                                             return value;
                                         }
                                     }
                                     """,
                                    "Integer id(Integer value)"),
                new UnsupportedCase("void method",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Valid
                                         void ping() {
                                         }
                                     }
                                     """,
                                    "void ping()"),
                new UnsupportedCase("constructor",
                                    """
                                     package com.example;

                                     import io.helidon.validation.Validation;

                                     @Validation.Validated
                                     public class Foo {
                                         @Validation.Valid
                                         Foo() {
                                         }
                                     }
                                     """,
                                    "Foo()")
        );
    }

    private TestCompiler.Result compile(String source) {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", source)
                .build()
                .compile();
    }

    private void assertGeneratedAndContainsCases(TestCompiler.Result result,
                                                 String typeName,
                                                 String... propertyNames) throws IOException {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("warning:")));

        var validator = result.sourceOutput().resolve("com/example/" + typeName + "__Validated.java");
        assertThat(Files.exists(validator), is(true));

        var content = Files.readString(validator, StandardCharsets.UTF_8);
        for (String propertyName : propertyNames) {
            assertThat(content, containsString("case \"" + propertyName + "\" -> check"
                                                       + capitalize(propertyName) + "(ctx, (Integer) value);"));
        }
    }

    private void assertCompilationFails(TestCompiler.Result result, String... diagnosticParts) {
        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        for (String diagnosticPart : diagnosticParts) {
            assertThat(diagnostics, containsString(diagnosticPart));
        }
    }

    private static String capitalize(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private record UnsupportedCase(String name, String source, String expectedElement) {
    }
}
