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

package io.helidon.json.schema.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Year;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

class SchemaGeneratedSourceMetadataTest {
    private static final Path GENERATED_SOURCES = Path.of("target",
                                                          "generated-test-sources",
                                                          "test-annotations",
                                                          "io",
                                                          "helidon",
                                                          "json",
                                                          "schema",
                                                          "tests");

    @Test
    void testGeneratedSchemaMetadata() throws IOException {
        Path generatedSource = GENERATED_SOURCES.resolve("SchemaCar__JsonSchema.java");
        assertThat("Generated source should exist: " + generatedSource, Files.exists(generatedSource), is(true));

        String content = Files.readString(generatedSource);

        assertThat(content, startsWith("/*"));
        assertThat(content, containsString("Copyright (c) "));
        assertThat(content, containsString(" Oracle and/or its affiliates."));
        assertThat(content, containsString("Licensed under the Apache License, Version 2.0"));
        assertThat(content, not(containsString("// This is a generated file (powered by Helidon).")));
        assertThat(content, containsString("@Generated("));
        assertThat(content, containsString("value = \"io.helidon.json.schema.codegen.SchemaCodegen\""));
        assertThat(content, containsString("trigger = \"io.helidon.json.schema.tests.SchemaCar\""));
    }
}
