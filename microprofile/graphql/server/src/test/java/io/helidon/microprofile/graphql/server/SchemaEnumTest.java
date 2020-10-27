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
        SchemaEnum schemaEnum1 = new SchemaEnum("ShirtSize");
        schemaEnum1.description("This is the description of the Enum");
        schemaEnum1.addValue("S");
        schemaEnum1.addValue("M");
        schemaEnum1.addValue("L");
        schemaEnum1.addValue("XL");
        schemaEnum1.addValue("XXL");
        schemaEnum1.addValue("3XL");

        assertThat(schemaEnum1.description(), is("This is the description of the Enum"));
        assertThat(schemaEnum1.values(), is(notNullValue()));
        assertThat(schemaEnum1.values().size(), is(6));
    }

    @Test
    public void testSchemaGenerationWithDescription() {
        SchemaEnum schemaEnum1 = new SchemaEnum("ShirtSize");
        schemaEnum1.description("T Shirt Size");
        schemaEnum1.addValue("Small");
        schemaEnum1.addValue("Medium");
        schemaEnum1.addValue("Large");
        schemaEnum1.addValue("XLarge");

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-01.txt");
    }

    @Test
    public void testSchemaGenerationWithoutDescription() {
        SchemaEnum schemaEnum1 = new SchemaEnum("ShirtSize");
        schemaEnum1.addValue("Small");
        schemaEnum1.addValue("Medium");
        schemaEnum1.addValue("Large");
        schemaEnum1.addValue("XLarge");

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-02.txt");
    }

    @Test
    public void testSchemaGenerationWithDescriptionAndQuote() {
        SchemaEnum schemaEnum1 = new SchemaEnum("ShirtSize");
        schemaEnum1.addValue("Small");
        schemaEnum1.addValue("Medium");
        schemaEnum1.addValue("Large");
        schemaEnum1.addValue("XLarge");
        schemaEnum1.description("Description\"");

        assertResultsMatch(schemaEnum1.getSchemaAsString(), "test-results/enum-test-03.txt");
    }
}
