/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.model;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link Enum} class.
 */
class EnumTest extends AbstractGraphQLTest {
    @Test
    public void testConstructor() {
        Enum enum1 = new Enum("ShirtSize");
        enum1.setDescription("This is the description of the Enum");
        enum1.addValue("S");
        enum1.addValue("M");
        enum1.addValue("L");
        enum1.addValue("XL");
        enum1.addValue("XXL");
        enum1.addValue("3XL");

        assertThat(enum1.getDescription(), is("This is the description of the Enum"));
        assertThat(enum1.getValues(), is(notNullValue()));
        assertThat(enum1.getValues().size(), is(6));
    }

    @Test
    public void testSchemaGenerationWithDescription() {
        Enum enum1 = new Enum("ShirtSize");
        enum1.setDescription("T Shirt Size");
        enum1.addValue("Small");
        enum1.addValue("Medium");
        enum1.addValue("Large");
        enum1.addValue("XLarge");

        assertResultsMatch(enum1.getSchemaAsString(), "test-results/enum-test-01.txt");
    }
    @Test
    public void testSchemaGenerationWithoutDescription() {
        Enum enum1 = new Enum("ShirtSize");
        enum1.addValue("Small");
        enum1.addValue("Medium");
        enum1.addValue("Large");
        enum1.addValue("XLarge");

        assertResultsMatch(enum1.getSchemaAsString(), "test-results/enum-test-02.txt");
    }
}