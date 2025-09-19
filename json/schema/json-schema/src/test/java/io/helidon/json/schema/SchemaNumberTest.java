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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaNumberTest {

    @Test
    void testBoundaries() {
        SchemaNumber schemaNumber = SchemaNumber.builder()
                .minimum(0)
                .maximum(1)
                .build();
        assertThat(schemaNumber.minimum().isPresent(), is(true));
        assertThat(schemaNumber.maximum().isPresent(), is(true));
        assertThat(schemaNumber.exclusiveMinimum().isPresent(), is(false));
        assertThat(schemaNumber.exclusiveMaximum().isPresent(), is(false));
        assertThat(schemaNumber.multipleOf().isPresent(), is(false));
        assertThat(schemaNumber.minimum().get(), is(0.0));
        assertThat(schemaNumber.maximum().get(), is(1.0));
    }

    @Test
    void testBoundariesExclusive() {
        SchemaNumber schemaNumber = SchemaNumber.builder()
                .exclusiveMinimum(0)
                .exclusiveMaximum(1)
                .build();
        assertThat(schemaNumber.minimum().isPresent(), is(false));
        assertThat(schemaNumber.maximum().isPresent(), is(false));
        assertThat(schemaNumber.exclusiveMinimum().isPresent(), is(true));
        assertThat(schemaNumber.exclusiveMaximum().isPresent(), is(true));
        assertThat(schemaNumber.multipleOf().isPresent(), is(false));
        assertThat(schemaNumber.exclusiveMinimum().get(), is(0.0));
        assertThat(schemaNumber.exclusiveMaximum().get(), is(1.0));
    }

    @Test
    void testBoundariesMixed() {
        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .minimum(1)
                .exclusiveMinimum(1)
                .build());

        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .maximum(1)
                .exclusiveMaximum(1)
                .build());
    }

    @Test
    void testLowerMaximum() {
        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .minimum(2)
                .maximum(1)
                .build());

        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .exclusiveMinimum(2)
                .exclusiveMaximum(1)
                .build());

        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .exclusiveMinimum(2)
                .maximum(1)
                .build());

        assertThrows(JsonSchemaException.class, () -> SchemaNumber.builder()
                .minimum(2)
                .exclusiveMaximum(1)
                .build());
    }

}
