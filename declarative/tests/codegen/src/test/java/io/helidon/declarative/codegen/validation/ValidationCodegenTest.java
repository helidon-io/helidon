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

import static io.helidon.codegen.CodegenUtil.capitalize;

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

                            @Validation.Integer.Min(1)
                            public Integer getAge() {
                                return 42;
                            }

                            @Validation.Integer.Min(1)
                            Integer size() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "age", "size");
    }

    @Test
    void testSupportedRecordElements() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public record Foo(@Validation.Integer.Min(1) Integer id,
                                          @Validation.Integer.Min(1) Integer age) {
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "age");
    }

    @Test
    void testSupportedInterfaceElements() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            Integer getId();

                            @Validation.Integer.Min(1)
                            Integer size();
                        }
                        """)
                .build()
                .compile();

        assertGeneratedAndContainsCases(result, "Foo", "id", "size");
    }

    @Test
    void testUnsupportedPrivateField() {
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

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "private Integer id");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (private field)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedStaticField() {
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
                            static Integer id;
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "static Integer id");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (static field)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedPrivateMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            private Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "private Integer id()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (private method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedStaticMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            static Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "static Integer id()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (static method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedMethodWithArguments() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            Integer id(Integer value) {
                                return value;
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "Integer id(Integer value)");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (method with arguments)",
                   Files.exists(validator),
                   is(false));
    }

    @Test
    void testUnsupportedVoidMethod() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            void ping() {
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "void ping()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (void method)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedConstructor() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public class Foo {
                            @Validation.Valid
                            Foo() {
                            }
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result,
                               "Only non-private fields and getter methods are supported",
                               "Foo()");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (constructor)", Files.exists(validator), is(false));
    }

    @Test
    void testInterfacePrivateAnnotatedMethodFailsCompilation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            private Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("unreachable statement"));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"id\" -> checkId(ctx, (Integer) value);")));
    }

    @Test
    void testInterfaceStaticAnnotatedMethodFailsCompilation() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            static Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("id"));
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should be generated (interface static method)",
                   Files.exists(validator),
                   is(true));
    }

    @Test
    void testInterfaceDefaultAnnotatedMethodSupported() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Integer.Min(1)
                            default Integer id() {
                                return 1;
                            }
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Compilation diagnostics: " + diagnostics, result.success(), is(true));
        assertThat(diagnostics, not(containsString("warning:")));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, containsString("case \"id\" -> checkId(ctx, (Integer) value);"));
    }

    @Test
    void testInterfaceAnnotatedMethodWithArgumentsFailsCompilation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Valid
                            Integer id(Integer value);
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("unreachable statement"));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"id\" -> checkId(ctx, (Integer) value);")));
    }

    @Test
    void testInterfaceAnnotatedVoidMethodFailsCompilation() throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public interface Foo {
                            @Validation.Valid
                            void ping();
                        }
                        """)
                .build()
                .compile();

        String diagnostics = String.join("\n", result.diagnostics());
        assertThat("Build should fail", result.success(), is(false));
        assertThat(diagnostics, containsString("error:"));
        assertThat(diagnostics, containsString("unreachable statement"));

        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat(Files.exists(validator), is(true));
        var content = Files.readString(validator, StandardCharsets.UTF_8);
        assertThat(content, not(containsString("case \"ping\" -> checkPing(ctx, (Integer) value);")));
    }

    @Test
    void testUnsupportedEnumType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        enum Foo {
                            INSTANCE
                        }
                        """)
                .build()
                .compile();
        assertCompilationFails(result, "Only record, class, or interface are currently supported");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (enum)", Files.exists(validator), is(false));
    }

    @Test
    void testUnsupportedAnnotationType() {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("Foo.java", """
                        package com.example;

                        import io.helidon.validation.Validation;

                        @Validation.Validated
                        public @interface Foo {
                            @Validation.Integer.Min(1)
                            int value();
                        }
                        """)
                .build()
                .compile();

        assertCompilationFails(result, "Only record, class, or interface are currently supported");
        var validator = result.sourceOutput().resolve("com/example/Foo__Validated.java");
        assertThat("The Foo__Validated.java should not be generated (annotation type)", Files.exists(validator), is(false));
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

}
