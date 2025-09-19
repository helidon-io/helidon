/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.json.schema;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaTest {

    @Test
    void testSchema() {
        Schema schema = Schema.builder()
                .rootObject(builder -> builder
                                  .addIntegerProperty("number", builder2 -> builder2.description("some number"))
                                  .addStringProperty("text", builder2 -> builder2.description("some text")))
                .build();
        SchemaObject root = schema.rootObject().orElseThrow();
        assertThat(root.properties().size(), is(2));
        assertThat(root.integerProperties().size(), is(1));
        assertThat(root.stringProperties().size(), is(1));
    }

    @Test
    void testSchemaMultipleRoots() {
        assertThrows(JsonSchemaException.class, () -> Schema.builder()
                .rootInteger(builder -> builder.multipleOf(1))
                .rootNumber(builder -> builder.multipleOf(1))
                .build());
    }

}
