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
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Builder;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.codegen.testing.CodegenMatchers.matches;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class ToStringTest {
    static Stream<Arguments> arrayCases() {
        return Stream.of(Arguments.of("byte", "Arrays.toString(data)"),
                         Arguments.of("short", "Arrays.toString(data)"),
                         Arguments.of("int", "Arrays.toString(data)"),
                         Arguments.of("long", "Arrays.toString(data)"),
                         Arguments.of("float", "Arrays.toString(data)"),
                         Arguments.of("double", "Arrays.toString(data)"),
                         Arguments.of("boolean", "Arrays.toString(data)"),
                         Arguments.of("char", "(data == null ? \"null\" : \"****\")"),
                         Arguments.of("String", "Arrays.toString(data)"),
                         Arguments.of("Object", "Arrays.toString(data)"));
    }

    @ParameterizedTest(name = "{0}[]")
    @MethodSource("arrayCases")
    void testGeneratedSource(String type, String expectedExpression) throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .addClasspath(List.of(Prototype.class, Builder.class))
                .addProcessor(AptProcessor::new)
                .addSource("PayloadBlueprint.java", """
                        package example;

                        import io.helidon.builder.api.Prototype;

                        @Prototype.Blueprint
                        interface PayloadBlueprint {
                            %s[] data();
                        }

                        """.formatted(type))
                .build()
                .compile();
        assertThat(result.diagnostics().toString(), result.success(), is(true));

        var generatedSource = result.sourceOutput().resolve("example/Payload.java");
        assertThat(Files.exists(generatedSource), is(true));
        assertThat(Files.readString(generatedSource), matches("""
                //...
                        public String toString() {
                            return "PayloadBuilder{"
                                    + "data=" + %s
                                    + "}";
                        }
                //...
                            public String toString() {
                                return "Payload{"
                                        + "data=" + %s
                                        + "}";
                            }
                //...
                """.formatted(expectedExpression, expectedExpression)));
    }
}
