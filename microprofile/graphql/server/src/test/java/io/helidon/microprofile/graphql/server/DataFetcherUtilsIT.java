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

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.AbstractVehicle;
import io.helidon.microprofile.graphql.server.test.types.Car;
import io.helidon.microprofile.graphql.server.test.types.ObjectWithIgnorableFieldsAndMethods;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithSelf;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests for {@link DataFetcherUtils} class.
 */
@ExtendWith(WeldJunit5Extension.class)
class DataFetcherUtilsIT
        extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesWithArgs.class)
                                                                .addBeanClass(SimpleContactWithNumberFormats.class)
                                                                .addBeanClass(SimpleContact.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    public void testSimpleContact() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        Map<String, Object> mapContact = Map.of("id", "1", "name", "Tim", "age", 52);
        SimpleContact simpleContact = new SimpleContact("1", "Tim", 52);
        assertArgumentResult(schema, "canFindContact", "contact", mapContact, simpleContact);
    }

    @Test
    public void testSimpleContactWithNumberFormats() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContactWithNumberFormats.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        // input SimpleContactWithNumberFormatsInput {
        //  "0 'years old'"
        //  age: String
        //  "¤ 000.00 en-AU"
        //  bankBalance: String
        //  "BigDecimal-##########"
        //  bigDecimal: String
        //  id: Int
        //  "LongValue-##########"
        //  longValue: String
        //  name: String
        //  "0 'value'"
        //  value: String!
        //}
        Map<String, Object> mapContact = Map.of("age",  "52 years old", "bankBalance", "$ 100.00",
                                                "bigDecimal", "BigDecimal-123", "longValue", "LongValue-321",
                                                "name", "Tim", "value", "1 value", "id", "100");
        SimpleContactWithNumberFormats contact =
                new SimpleContactWithNumberFormats(100, "Tim", 52, 100.0f, 1, 321L, BigDecimal.valueOf(123));
        assertArgumentResult(schema, "canFindSimpleContactWithNumberFormats", "contact", mapContact, contact);
    }

    @Test
    public void testSimpleTypes() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        // ID types
        assertArgumentResult(schema, "returnIntegerAsId", "param1", 1, 1);

        UUID uuid = UUID.randomUUID();
        assertArgumentResult(schema,"returnUUIDAsId", "param1", uuid, uuid);
        assertArgumentResult(schema, "returnStringAsId", "param1", "abc", "abc");
        assertArgumentResult(schema, "returnStringAsId", "param1", "abc", "abc");
        assertArgumentResult(schema, "returnLongAsId", "param1", 1L, 1L);
        assertArgumentResult(schema, "returnLongPrimitiveAsId", "param1", 1L, 1L);
        assertArgumentResult(schema, "returnIntPrimitiveAsId", "param1", 2, 2);

        // primitive types
        assertArgumentResult(schema, "echoString", "String", "string-value", "string-value");
        assertArgumentResult(schema, "echoInt", "value", 1, 1);
        assertArgumentResult(schema, "echoDouble", "value", 100d, 100d);
        assertArgumentResult(schema, "echoFloat", "value", 1.1f, 1.1f);
        assertArgumentResult(schema, "echoByte", "value", (byte) 10, (byte) 10);
        assertArgumentResult(schema, "echoLong", "value", 123L, 123L);
        assertArgumentResult(schema, "echoBoolean", "value", true, true);
        assertArgumentResult(schema, "echoBoolean", "value", false, false);
        assertArgumentResult(schema, "echoChar", "value", 'x', 'x');

        // Object types
        assertArgumentResult(schema, "echoIntegerObject", "value", 1, 1);
        assertArgumentResult(schema, "echoDoubleObject", "value", 100d, 100d);
        assertArgumentResult(schema, "echoFloatObject", "value", 1.1f, 1.1f);
        assertArgumentResult(schema, "echoFloatObject", "value", 1.1f, 1.1f);
        assertArgumentResult(schema, "echoByteObject", "value", (byte) 10, (byte) 10);
        assertArgumentResult(schema, "echoLongObject", "value", 123L, 123L);
        assertArgumentResult(schema, "echoBooleanObject", "value", true, true);
        assertArgumentResult(schema, "echoBooleanObject", "value", false, false);
        assertArgumentResult(schema, "echoCharacterObject", "value", 'x', 'x');

        assertArgumentResult(schema, "echoBigDecimal", "value", BigDecimal.valueOf(100.12), BigDecimal.valueOf(100.12));
        assertArgumentResult(schema, "echoBigInteger", "value", BigInteger.valueOf(100), BigInteger.valueOf(100));

        // Date/Time/DateTime are dealt with in DateTimeIT.java
    }

    @Test
    public void testSimpleTypesWithFormats() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        // primitives
        assertArgumentResult(schema, "echoIntWithFormat", "value", "100 value", 100);
        assertArgumentResult(schema, "echoDoubleWithFormat", "value", "11-format", 11d);
        assertArgumentResult(schema, "echoFloatWithFormat", "value", "$ 123.23", 123.23f);
        assertArgumentResult(schema, "echoLongWithFormat", "value", "Long-123456", 123456L);

        // objects
        assertArgumentResult(schema, "echoIntegerObjectWithFormat", "value", "100 value", 100);
        assertArgumentResult(schema, "echoDoubleObjectWithFormat", "value", "11-format", 11d);
        assertArgumentResult(schema, "echoFloatObjectWithFormat", "value", "$ 123.23", 123.23f);
        assertArgumentResult(schema, "echoLongObjectWithFormat", "value", "Long-123456", 123456L);

        assertArgumentResult(schema, "echoBigDecimalWithFormat", "value", "100-BigDecimal", BigDecimal.valueOf(100.0));
        assertArgumentResult(schema, "echoBigIntegerWithFormat", "value", "100-BigInteger", BigInteger.valueOf(100));

        // ID
        assertArgumentResult(schema, "returnIntegerAsIdWithFormat", "param1", "1 format", 1);
        assertArgumentResult(schema, "returnLongAsIdWithFormat", "param1", "1-Long", 1L);
        assertArgumentResult(schema, "returnLongPrimitiveAsIdWithFormat", "param1", "2-long", 2L);
        assertArgumentResult(schema, "returnIntPrimitiveAsIdWithFormat", "param1", "3 hello", 3);
        }

    @Test
    public void testArrays() {

    }
    @Test
    public void testArraysAndCollections() {

    }

    @Test
    public void testObjectGraphs() {

    }

    /**
     * Validate that the given argument results in the correct value.
     * @param schema   {@link Schema}
     * @param fdName   the name of the {@link SchemaFieldDefinition}
     * @param argumentName the name of the {@link SchemaArgument}
     * @param input      the input
     * @param expected   the expected output
     * @throws Exception if any errors
     */
    protected void assertArgumentResult(Schema schema, String fdName,
                                        String argumentName, Object input, Object expected) throws Exception {
        SchemaArgument argument = getArgument(schema, "Query", fdName, argumentName);
        assertThat(argument, is(notNullValue()));
        Object result = DataFetcherUtils.generateArgumentValue(schema, argument, input);
        assertThat(result, is(expected));
    }

    protected SchemaArgument getArgument(Schema schema, String typeName, String fdName, String argumentName) {
        SchemaType type = schema.getTypeByName(typeName);
        if (type != null) {
            SchemaFieldDefinition fd = getFieldDefinition(type, fdName);
            if (fd != null) {
                return fd.getArguments().stream()
                        .filter(a -> a.getArgumentName().equals(argumentName))
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

}
