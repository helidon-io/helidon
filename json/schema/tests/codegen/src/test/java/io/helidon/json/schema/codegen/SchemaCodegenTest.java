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

package io.helidon.json.schema.codegen;

import io.helidon.builder.api.Prototype;
import io.helidon.codegen.apt.AptProcessor;
import io.helidon.codegen.testing.TestCompiler;
import io.helidon.common.LazyValue;
import io.helidon.json.JsonValue;
import io.helidon.json.schema.JsonSchema;
import io.helidon.service.registry.Service;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class SchemaCodegenTest {

    @Test
    void testRejectsTrailingDefaultJsonValue() {
        TestCompiler.Result result = compileDefaultJson("true false");

        assertThat(result.success(), is(false));
    }

    @Test
    void testAcceptsTrailingDefaultJsonWhitespace() {
        TestCompiler.Result result = compileDefaultJson("true ");

        assertThat(result.success(), is(true));
    }

    @Test
    void testRejectsInvalidDefaultJsonNumber() {
        TestCompiler.Result result = compileDefaultJson("01");

        assertThat(result.success(), is(false));
    }

    @Test
    void testRejectsMultipleDefaultAnnotations() {
        TestCompiler.Result result = compileDefaultAnnotations("@JsonSchema.Default(\"value\")\n"
                                                                       + "@JsonSchema.DefaultInt(1)");

        assertThat(result.success(), is(false));
    }

    private static TestCompiler.Result compileDefaultJson(String defaultValue) {
        return compileDefaultAnnotations("@JsonSchema.DefaultJson(\"" + defaultValue + "\")");
    }

    private static TestCompiler.Result compileDefaultAnnotations(String defaultAnnotations) {
        return TestCompiler.builder()
                .currentRelease()
                .procOnly()
                .printDiagnostics(false)
                .addClasspath(JsonSchema.class)
                .addClasspath(JsonValue.class)
                .addClasspath(Prototype.class)
                .addClasspath(LazyValue.class)
                .addClasspath(Service.class)
                .addProcessor(AptProcessor::new)
                .addSource("DefaultSchema.java", """
                        package com.acme;

                        import io.helidon.json.schema.JsonSchema;

                        @JsonSchema.Schema
                        %s
                        class DefaultSchema {
                        }
                        """.formatted(defaultAnnotations))
                .build()
                .compile();
    }
}
