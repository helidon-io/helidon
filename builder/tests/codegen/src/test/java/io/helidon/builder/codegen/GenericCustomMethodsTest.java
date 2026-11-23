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

package io.helidon.builder.codegen;

import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class GenericCustomMethodsTest {
    @ParameterizedTest
    @ValueSource(strings = {"<OWNER>", ""})
    void testRuntimeFactoryCompiles(String ownerTypeParameters) {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(Prototype.class, Builder.class))
                .addProcessor(AptProcessor::new)
                .addSource("RuntimeConfigBlueprint.java", """
                        package example;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.CustomMethods(ValueFactory.class)
                        interface RuntimeConfigBlueprint<T> extends Prototype.Factory<Value<T>> {
                            T value();
                        }
                        """)
                .addSource("Value.java", """
                        package example;

                        record Value<T>(T value) {
                        }
                        """)
                .addSource("ValueFactory.java", """
                        package example;

                        import io.helidon.builder.api.Prototype;

                        class ValueFactory%s {
                            @Prototype.RuntimeTypeFactoryMethod
                            static <T> Value<T> create(RuntimeConfig<T> prototype) {
                                return new Value<>(prototype.value());
                            }
                        }
                        """.formatted(ownerTypeParameters))
                .addSource("Caller.java", """
                        package example;

                        class Caller {
                            Value<String> stringFromBuilder() {
                                return RuntimeConfig.<String>builder().value("builder").build();
                            }

                            Value<Integer> integerFromBuilder() {
                                return RuntimeConfig.<Integer>builder().value(1).build();
                            }

                            Value<String> stringFromPrototype() {
                                return RuntimeConfig.<String>builder().value("prototype").buildPrototype().build();
                            }

                            Value<Integer> integerFromPrototype() {
                                return RuntimeConfig.<Integer>builder().value(2).buildPrototype().build();
                            }
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<OWNER>", ""})
    void testCustomMethodsAndConstantCompile(String ownerTypeParameters) {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(Prototype.class, Builder.class))
                .addProcessor(AptProcessor::new)
                .addSource("ExampleBlueprint.java", """
                        package example;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        @Prototype.CustomMethods(Value.class)
                        interface ExampleBlueprint {
                            String name();
                        }
                        """)
                .addSource("Value.java", """
                        package example;

                        import java.util.List;

                        import io.helidon.builder.api.Prototype;

                        class Value%s {
                            @Prototype.Constant
                            static final String DEFAULT_NAME = "name";

                            @Prototype.BuilderMethod
                            static <U extends CharSequence> void withName(Example.BuilderBase<?, ?> builder, U name) {
                                builder.name(name.toString());
                            }

                            @Prototype.PrototypeMethod
                            static <U> U echo(Example prototype, U value) {
                                return value;
                            }

                            @Prototype.PrototypeFactoryMethod
                            static <U> List<U> singleton(U value) {
                                return List.of(value);
                            }
                        }
                        """.formatted(ownerTypeParameters))
                .addSource("Caller.java", """
                        package example;

                        import java.util.List;

                        class Caller {
                            Example create() {
                                return Example.builder()
                                        .<StringBuilder>withName(new StringBuilder(Example.DEFAULT_NAME))
                                        .build();
                            }

                            String echoString(Example prototype) {
                                return prototype.echo("text");
                            }

                            Integer echoInteger(Example prototype) {
                                return prototype.echo(1);
                            }

                            List<String> strings() {
                                return Example.singleton("text");
                            }

                            List<Integer> integers() {
                                return Example.singleton(1);
                            }
                        }
                        """)
                .build()
                .compile();

        assertThat(result.diagnostics().toString(), result.success(), is(true));
    }
}
