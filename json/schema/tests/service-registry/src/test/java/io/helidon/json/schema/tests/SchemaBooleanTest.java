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

import io.helidon.json.schema.Schema;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

class SchemaBooleanTest {

    @Test
    void testGeneratedBooleanProperties() {
        String generatedSchema = new SchemaBooleans__JsonSchema().jsonSchema();
        var root = Schema.parse(generatedSchema).rootObject().orElseThrow();

        assertThat("Boolean properties in generated schema: " + generatedSchema,
                   root.booleanProperties().keySet(),
                   containsInAnyOrder("primitive", "boxed"));

        var primitive = root.booleanProperties().get("primitive");
        assertThat(primitive.required(), is(false));

        var boxed = root.booleanProperties().get("boxed");
        assertThat(boxed.title().orElseThrow(), is("Boxed boolean"));
        assertThat(boxed.description().orElseThrow(), is("A required boolean value"));
        assertThat(boxed.required(), is(true));
    }
}
