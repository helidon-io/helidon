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

package io.helidon.microprofile.graphql.server.util;

import java.util.Set;

import io.helidon.microprofile.graphql.server.AbstractGraphQLTest;
import io.helidon.microprofile.graphql.server.model.Enum;
import io.helidon.microprofile.graphql.server.model.Schema;
import io.helidon.microprofile.graphql.server.test.EnumTestNoEnumName;
import io.helidon.microprofile.graphql.server.test.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.EnumTestWithNameAndNameAnnotation;
import io.helidon.microprofile.graphql.server.test.EnumTestWithNameAnnotation;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for {@link SchemaUtilsTest}.
 */
public class SchemaUtilsTest extends AbstractGraphQLTest {

    @Test
    public void testEnumGeneration() {
        testEnum(EnumTestNoEnumName.class, EnumTestNoEnumName.class.getSimpleName());

        testEnum(EnumTestWithEnumName.class, "TShirtSize");
        
        testEnum(EnumTestWithNameAnnotation.class, "TShirtSize");

        testEnum(EnumTestWithNameAndNameAnnotation.class, "ThisShouldWin");
    }

    private void testEnum(Class<?> clazz, String expectedName) {
        Schema schema = SchemaUtils.generateSchemaFromClasses(clazz);
        assertThat(schema, is(notNullValue()));

        assertThat(schema.getEnums().size(), is(1));
        Enum enumResult = schema.getEnumByName(expectedName);

        assertThat(enumResult, is(notNullValue()));
        assertThat(enumResult.getValues().size(), is(6));

        Set.of("S","M","L","XL","XXL","XXXL").forEach(v -> assertThat(enumResult.getValues().contains(v), is(true)));
    }

}
