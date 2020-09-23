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

import static io.helidon.microprofile.graphql.server.JsonUtils.convertObjectToMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.ContactRelationship;
import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;

import io.helidon.microprofile.tests.junit5.AddBean;

import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;


/**
 * Tests for {@link DataFetcherUtils} class.
 */

@HelidonTest
@DisableDiscovery
@AddExtension(GraphQLCdiExtension.class)
@AddBean(SimpleQueriesWithArgs.class)
@AddBean(SimpleContactWithNumberFormats.class)
@AddBean(ContactRelationship.class)
@AddBean(SimpleContact.class)
@AddBean(TestDB.class)
class DataFetcherUtilsIT
        extends AbstractGraphQLIT {

    @Test
    public void testSimpleContact() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        Map<String, Object> mapContact = Map.of("id", "1", "name", "Tim", "age", 52, "tShirtSize", "L");
        SimpleContact simpleContact = new SimpleContact("1", "Tim", 52, EnumTestWithEnumName.L);
        assertArgumentResult(schema, "canFindContact", "contact", mapContact, simpleContact);
    }

    @Test
    public void testSimpleContactWithNumberFormats() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContactWithNumberFormats.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        Map<String, Object> mapContact = Map.of("age", "52 years old", "bankBalance", "$ 100.00",
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
        assertArgumentResult(schema, "returnUUIDAsId", "param1", uuid, uuid);
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
    public void testArrays() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        String[] stringArray = new String[] {"A", "B", "C"};
        assertArgumentResult(schema, "echoStringArray", "value", stringArray, stringArray);

        int[] intArray = new int[] {1, 2, 3, 4};
        assertArgumentResult(schema, "echoIntArray", "value", intArray, intArray);

        Boolean[] booleanArray = new Boolean[] {true, false, true, true};
        assertArgumentResult(schema, "echoBooleanArray", "value", booleanArray, booleanArray);

        String[][] stringArray2 = new String[][] {
                { "A", "B", "C"},
                { "D", "E", "F"}
        };
        assertArgumentResult(schema, "echoStringArray2", "value", stringArray2, stringArray2);

        SimpleContact[] contactArray = new SimpleContact[] {
               new SimpleContact("c1", "Contact 1", 50, EnumTestWithEnumName.XL),
                new SimpleContact("c2", "Contact 2", 52, EnumTestWithEnumName.XL)
        };
        assertArgumentResult(schema, "echoSimpleContactArray", "value", contactArray, contactArray);

        // TODO: Test formatting of numbers and dates in arrays
        
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSimpleCollections() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        List<Integer> listInteger = getList(1, 2, 3);
        List<String> listString = getList("A", "B", "C");
        Collection<BigInteger> colBigInteger = getList(BigInteger.valueOf(1), BigInteger.valueOf(222), BigInteger.valueOf(333));

        assertArgumentResult(schema, "echoListOfIntegers", "value", listInteger, listInteger);
        assertArgumentResult(schema, "echoListOfStrings", "value", listString, listString);
        assertArgumentResult(schema, "echoListOfBigIntegers", "value", colBigInteger, colBigInteger);

        // Test formatting for numbers and dates
        List<String> listFormattedIntegers = List.of("1 years old", "3 years old", "53 years old");
        assertArgumentResult(schema, "echoFormattedListOfIntegers", "value", listFormattedIntegers,
                             List.of(1, 3, 53));

        LocalDate localDate1 = LocalDate.of(2020, 9, 23);
        LocalDate localDate2 = LocalDate.of(2020, 9, 22);
        List<String> listLocalDates = List.of("23-09-2020", "22-09-2020");
        assertArgumentResult(schema, "echoFormattedLocalDate", "value", listLocalDates,
                     List.of(localDate1, localDate2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCollectionsAndObjects() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50, EnumTestWithEnumName.XL);
        SimpleContact contact2 = new SimpleContact("c2", "Contact 2", 52, EnumTestWithEnumName.XXL);

        Collection<SimpleContact> colContacts = List.of(contact1, contact2);
        Collection<Map<String, Object>> listOfMaps = List.of(convertObjectToMap(contact1), convertObjectToMap(contact2));

        assertArgumentResult(schema, "echoCollectionOfSimpleContacts", "value",
                             listOfMaps, colContacts);

        // test multi-level collections
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testObjectGraphs() throws Exception {
        setupIndex(indexFileName, SimpleQueriesWithArgs.class, SimpleContact.class, ContactRelationship.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Schema schema = executionContext.getSchema();

        SimpleContact contact1 = new SimpleContact("c1", "Contact 1", 50, EnumTestWithEnumName.M);
        SimpleContact contact2 = new SimpleContact("c2", "Contact 2", 53, EnumTestWithEnumName.L);
        ContactRelationship relationship = new ContactRelationship(contact1, contact2, "married");

        Map<String, Object> contact1Map = convertObjectToMap(contact1);
        Map<String, Object> contact2Map = convertObjectToMap(contact2);

        // Create a map representing the above contact relationship
        Map<String, Object> mapContactRel = Map.of("relationship", "married",
                                                   "contact1", contact1Map,
                                                   "contact2", contact2Map);

        assertArgumentResult(schema, "canFindContactRelationship", "relationship", mapContactRel, relationship);
    }

    @SuppressWarnings( { "rawtypes", "unchecked" })
    protected List getList(Object... values) {
        ArrayList list = new ArrayList();
        for (Object value : values) {
            list.add(value);
        }
        return list;
    }

    /**
     * Validate that the given argument results in the correct value.
     *
     * @param schema       {@link Schema}
     * @param fdName       the name of the {@link SchemaFieldDefinition}
     * @param argumentName the name of the {@link SchemaArgument}
     * @param input        the input
     * @param expected     the expected output
     * @throws Exception if any errors
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected void assertArgumentResult(Schema schema, String fdName,
                                        String argumentName, Object input, Object expected) throws Exception {
        SchemaArgument argument = getArgument(schema, "Query", fdName, argumentName);
        assertThat(argument, is(notNullValue()));
        Object result = DataFetcherUtils.generateArgumentValue(schema, argument.getArgumentType(),
                                                               argument.getOriginalType(), argument.getOriginalArrayType(),
                                                               input, argument.getFormat());

        if (input instanceof Collection) {
            // compare each value
            Collection colExpected = (Collection) expected;
            Collection colResult = (Collection) result;
            for (Object value : colExpected) {
                if (!colResult.contains(value)) {
                    throw new AssertionError("Cannot find expected value [" +
                                             value + "] in result " + colResult.toString());
                }
            }
        } else {
            assertThat(result, is(expected));
        }
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
