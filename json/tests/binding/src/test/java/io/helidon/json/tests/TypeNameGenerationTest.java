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

package io.helidon.json.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.codegen.api.stability.ApiStabilityProcessor;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.Generated;
import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.Json;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

@SuppressWarnings({Api.SUPPRESS_INTERNAL, Api.SUPPRESS_PREVIEW})
class TypeNameGenerationTest {
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Bytes.class,
            JsonGenerator.class,
            Json.class,
            Service.class);

    @Test
    void recursiveBoundsDoNotBecomeConverterFieldNames(@TempDir Path tempDir) throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                // Full compilation separately fails because bounded factory arguments are replaced with Object.
                .procOnly()
                .workDir(tempDir)
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource("BuilderBase.java", """
                        package io.helidon.json.tests.generated;

                        import io.helidon.json.binding.Json;

                        @Json.Entity
                        public class BuilderBase<BUILDER extends BuilderBase<BUILDER, PROTOTYPE>,
                                                 PROTOTYPE extends BuilderBase.MyTestPerson> {
                            public BUILDER builder;
                            public PROTOTYPE prototype;

                            public static class MyTestPerson {
                            }
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join(System.lineSeparator(), result.diagnostics()), result.success(), is(true));
        String generated = Files.readString(result.sourceOutput()
                                                    .resolve("io/helidon/json/tests/generated/BuilderBase_BindingFactory.java"));
        // Names must come from the declared type variable, not a type embedded in its bounds.
        assertThat(generated, containsString("serializerBUILDER"));
        assertThat(generated, containsString("serializerPROTOTYPE"));
        assertThat(generated, containsString("deserializerBUILDER"));
        assertThat(generated, containsString("deserializerPROTOTYPE"));
    }

    @Test
    void nestedTypesAndArrayDimensionsCompile(@TempDir Path tempDir) throws IOException {
        var result = TestCompiler.builder()
                .currentRelease()
                .workDir(tempDir)
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addProcessor(ApiStabilityProcessor::new)
                .addOption("-Ahelidon.api=fail")
                .addSource("NestedTypes.java", """
                        package io.helidon.json.tests.generated;

                        import java.util.List;

                        import io.helidon.json.binding.Json;

                        public class NestedTypes {
                            @Json.Entity
                            public record Matrix(Left.Value[][] left,
                                                 Right.Value[][] right,
                                                 int[][] numbers,
                                                 Box<List<Left.Value>> nested) {
                            }

                            @Json.Entity
                            public static class Box<T> {
                                private T value;

                                public T getValue() {
                                    return value;
                                }

                                public void setValue(T value) {
                                    this.value = value;
                                }
                            }

                            public static class Left {
                                @Json.Entity
                                public record Value(String text) {
                                }
                            }

                            public static class Right {
                                @Json.Entity
                                public record Value(int number) {
                                }
                            }
                        }
                        """)
                .build()
                .compile();

        assertThat(String.join(System.lineSeparator(), result.diagnostics()), result.success(), is(true));
        String generated = Files.readString(result.sourceOutput()
                                                    .resolve("io/helidon/json/tests/generated/NestedTypes_Matrix"
                                                                     + "__GeneratedConverter.java"));
        assertThat(generated, containsString("serializerValueArrayArray;"));
        assertThat(generated, containsString("serializerValueArrayArray_1;"));
        assertThat(generated, containsString("serializerIntArrayArray;"));
    }
}
