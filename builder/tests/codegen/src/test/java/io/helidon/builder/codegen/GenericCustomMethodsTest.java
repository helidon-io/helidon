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

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;
import io.helidon.common.mapper.OptionalValue;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.matchesPattern;

class GenericCustomMethodsTest {
    /**
     * Verifies explicit generated boundaries and their documentation, including checks before inherited copy delegation.
     */
    @Test
    void testConfiguredFactoryAndCopyNullContracts() throws IOException {
        var compiler = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(Prototype.class, Option.class, Builder.class, OptionalValue.class,
                                      Config.class, Configured.class))
                .addProcessor(AptProcessor::new)
                .build();
        var parentResult = TestCompiler.builder().from(compiler)
                .addSource("ParentBlueprint.java", """
                        package example;
                        import io.helidon.builder.api.Option;
                        import io.helidon.builder.api.Prototype;
                        /**
                         * Defines the configured parent used to verify copy delegation.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface ParentBlueprint {
                            @Option.Configured
                            @Option.Default("SELECT 1")
                            String query();
                        }
                        """)
                .addSource("GenericBlueprint.java", """
                        package example;
                        import io.helidon.builder.api.Prototype;
                        /**
                         * Defines a generic prototype used to verify factory validation.
                         */
                        @Prototype.Blueprint
                        interface GenericBlueprint<T> {
                            T value();
                        }
                        """)
                .build().compile();
        assertThat(parentResult.diagnostics().toString(), parentResult.success(), is(true));
        var result = TestCompiler.builder().from(compiler)
                .addClasspathEntry(parentResult.classOutput())
                .addSource("PolicyBlueprint.java", """
                        package example;
                        import io.helidon.builder.api.Option;
                        import io.helidon.builder.api.Prototype;
                        /**
                         * Defines a policy with a primitive discovery flag.
                         */
                        @Prototype.Blueprint
                        @Prototype.Configured
                        interface PolicyBlueprint extends Parent {
                            @Option.Configured
                            @Option.DefaultBoolean(false)
                            boolean enabled();
                        }
                        """)
                .addSource("OwnerBlueprint.java", """
                        package example;
                        import io.helidon.builder.api.Prototype;
                        /**
                         * Defines an owner with typed policy, consumer, and supplier setters.
                         */
                        @Prototype.Blueprint
                        interface OwnerBlueprint {
                            Policy health();
                        }
                        """)
                .build().compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));
        String policy = Files.readString(result.sourceOutput().resolve("example/Policy.java"));
        for (var entry : Map.of("builder(Policy instance)", "instance",
                                "create(Config config)", "config",
                                "from(Policy prototype)", "prototype",
                                "from(Policy.BuilderBase", "builder",
                                "config(Config config)", "config").entrySet()) {
            assertNullContract(policy, entry.getKey(), entry.getValue());
        }
        int primitive = policy.indexOf("enabled(boolean enabled)");
        assertThat(primitive, greaterThanOrEqualTo(0));
        assertThat(policy.substring(policy.lastIndexOf("/**", primitive), primitive),
                   not(containsString("NullPointerException")));
        String parent = Files.readString(parentResult.sourceOutput().resolve("example/Parent.java"));
        assertNullContract(parent, "query(String query)", "query");
        String owner = Files.readString(result.sourceOutput().resolve("example/Owner.java"));
        String generic = Files.readString(parentResult.sourceOutput().resolve("example/Generic.java"));
        assertNullContract(generic, "builder(Generic<T> instance)", "instance");
        assertNullContract(generic, "from(Generic<T> prototype)", "prototype");
        assertNullContract(owner, "health(Policy health)", "health");
        assertNullContract(owner, "health(Consumer<Policy.Builder> consumer)", "consumer");
        assertNullContract(owner, "health(Supplier<? extends Policy> supplier)", "supplier");
    }

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

    private static void assertNullContract(String source, String signature, String parameter) {
        int declaration = source.indexOf(signature);
        assertThat("The generated source must declare " + signature + ".", declaration, greaterThanOrEqualTo(0));
        int comment = source.lastIndexOf("/**", declaration);
        assertThat(source.substring(comment, declaration), containsString("@throws java.lang.NullPointerException"));
        int commentEnd = source.indexOf("*/", comment);
        assertThat(source.substring(commentEnd + 2, declaration),
                   matchesPattern("\\s*(?:@Override\\s+)?(?:public|static)\\s+[^;{}]*"));
        int body = source.indexOf('{', declaration);
        assertThat(source.substring(body + 1).stripLeading(), startsWith("Objects.requireNonNull(" + parameter + ");"));
    }
}
