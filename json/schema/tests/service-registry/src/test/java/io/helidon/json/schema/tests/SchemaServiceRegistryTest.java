/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.util.Optional;

import io.helidon.json.schema.Schema;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SchemaServiceRegistryTest {

    @Test
    public void testSchemaFromServiceRegistry() {
        String expected = """
                {
                   "$schema": "https://json-schema.org/draft/2020-12/schema",
                   "description": "My super car",
                   "type": "object",
                   "properties": {
                      "color": {
                         "description": "The color of my car",
                         "type": "string"
                      },
                      "spz": {
                         "type": "string"
                      }
                   },
                   "required": [
                      "spz"
                   ]
                }""";
        Optional<Schema> optionalSchema = Schema.find(SchemaCar.class);

        assertThat(optionalSchema.isPresent(), is(true));
        Schema schema = optionalSchema.get();

        assertThat(schema.generate(), is(expected));
    }

    @Test
    public void testSchemaFromServiceRegistryInvalid() {
        Optional<Schema> optionalSchema = Schema.find(SomeRegularCar.class);

        assertThat(optionalSchema.isEmpty(), is(true));
    }

}
