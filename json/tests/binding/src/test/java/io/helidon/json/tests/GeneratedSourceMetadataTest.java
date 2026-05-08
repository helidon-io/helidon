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

import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.Api;
import io.helidon.common.Generated;
import io.helidon.common.buffers.Bytes;
import io.helidon.json.JsonGenerator;
import io.helidon.json.binding.Json;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

@SuppressWarnings(Api.SUPPRESS_INTERNAL)
class GeneratedSourceMetadataTest {
    private static final String PACKAGE_NAME = "io.helidon.json.tests.generated";
    private static final String PACKAGE_PATH = PACKAGE_NAME.replace('.', '/') + "/";
    private static final String GENERATOR = "io.helidon.json.codegen.JsonCodegen";
    private static final List<Class<?>> CLASSPATH = List.of(
            Generated.class,
            Bytes.class,
            JsonGenerator.class,
            Json.class,
            Service.class);

    @Test
    void testGeneratedConverterMetadata() throws IOException {
        var result = compile("StringWrapper.java", """
                package io.helidon.json.tests.generated;
                
                import io.helidon.json.binding.Json;
                
                @Json.Entity
                public class StringWrapper {
                    private String value;
                
                    public String getValue() {
                        return value;
                    }
                
                    public void setValue(String value) {
                        this.value = value;
                    }
                }
                """);

        assertCompilationSuccess(result);
        assertGeneratedSourceMetadata(generatedSource(result, "StringWrapper__GeneratedConverter.java"),
                                      GENERATOR,
                                      PACKAGE_NAME + ".StringWrapper");
    }

    @Test
    void testGeneratedBindingFactoryMetadata() throws IOException {
        var result = compile("Container.java", """
                package io.helidon.json.tests.generated;
                
                import io.helidon.json.binding.Json;
                
                @Json.Entity
                public class Container<T> {
                    private T value;
                
                    public T getValue() {
                        return value;
                    }
                
                    public void setValue(T value) {
                        this.value = value;
                    }
                }
                """);

        assertCompilationSuccess(result);
        assertGeneratedSourceMetadata(generatedSource(result, "Container_BindingFactory.java"),
                                      GENERATOR,
                                      PACKAGE_NAME + ".Container<T>");
    }

    private static TestCompiler.Result compile(String sourceName, String source) {
        return TestCompiler.builder()
                .currentRelease()
                .addClasspath(CLASSPATH)
                .addProcessor(AptProcessor::new)
                .addSource(sourceName, source)
                .build()
                .compile();
    }

    private static void assertCompilationSuccess(TestCompiler.Result result) {
        assertThat(String.join(System.lineSeparator(), result.diagnostics()), result.success(), is(true));
    }

    private static Path generatedSource(TestCompiler.Result result, String fileName) {
        return result.sourceOutput().resolve(PACKAGE_PATH + fileName);
    }

    private static void assertGeneratedSourceMetadata(Path generatedSource,
                                                      String generator,
                                                      String trigger) throws IOException {
        assertThat("Generated source should exist: " + generatedSource, Files.exists(generatedSource), is(true));

        String content = Files.readString(generatedSource);

        assertThat(content, startsWith("/*"));
        assertThat(content, containsString("Copyright (c) "));
        assertThat(content, containsString(" Oracle and/or its affiliates."));
        assertThat(content, containsString("Licensed under the Apache License, Version 2.0"));
        assertThat(content, not(containsString("// This is a generated file (powered by Helidon).")));
        assertThat(content, containsString("@Generated("));
        assertThat(content, containsString("value = \"" + generator + "\""));
        assertThat(content, containsString("trigger = \"" + trigger + "\""));
    }
}
