/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.microprofile.graphql.server;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaEnum} class.
 */
class SchemaEnumTest extends AbstractGraphQLTest {

    @Test
    public void testConstructor() {

        SchemaEnum schemaEnum1 = SchemaEnum.builder()
                .name("ShirtSize")
                .description("This is the description of the Enum")
                .addValue("S")
                .addValue("M")
                .addValue("L")
                .addValue("XL")
                .addValue("XXL")
                .addValue("3XL")
                .build();

        assertThat(schemaEnum1.description(), is("This is the description of the Enum"));
        assertThat(schemaEnum1.values(), is(notNullValue()));
        assertThat(schemaEnum1.values().size(), is(6));
    }

    @Test
    public void testSchemaGenerationWithDescription() {
        SchemaEnum schemaEnum1 = SchemaEnum.builder()
                .name("ShirtSize")
                .description("T Shirt Size")
                .addValue("Small")
                .addValue("Medium")
                .addValue("Large")
                .addValue("XLarge")
                .build();

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-01.txt");
    }

    @Test
    public void testSchemaGenerationWithoutDescription() {
        SchemaEnum schemaEnum1 = SchemaEnum.builder()
                .name("ShirtSize")
                .addValue("Small")
                .addValue("Medium")
                .addValue("Large")
                .addValue("XLarge")
                .build();

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-02.txt");
    }

    @Test
    public void testSchemaGenerationWithDescriptionAndQuote() {
        SchemaEnum schemaEnum1 = SchemaEnum.builder()
                .name("ShirtSize")
                .addValue("Small")
                .addValue("Medium")
                .addValue("Large")
                .addValue("XLarge")
                .description("Description\"")
                .build();

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-03.txt");
    }
}
