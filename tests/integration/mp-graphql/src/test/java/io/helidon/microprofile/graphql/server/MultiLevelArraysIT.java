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

import java.beans.IntrospectionException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.enums.EnumTestWithEnumName;
import io.helidon.microprofile.graphql.server.test.queries.ArrayAndListQueries;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;

import io.helidon.microprofile.graphql.server.test.types.SimpleContact;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

/**
 * Tests for Multi-level arrays.
 */
@AddBean(MultiLevelListsAndArrays.class)
@AddBean(ArrayAndListQueries.class)
@AddBean(TestDB.class)
public class MultiLevelArraysIT extends AbstractGraphQLIT {

    @Test
    public void testMultipleLevelsOfGenerics() throws IntrospectionException, ClassNotFoundException, IOException {
        setupIndex(indexFileName, MultiLevelListsAndArrays.class);
        SchemaGenerator schemaGenerator = new SchemaGenerator(defaultContext);
        Schema schema = schemaGenerator.generateSchema();
        assertThat(schema.containsTypeWithName("MultiLevelListsAndArrays"), is(true));
        assertThat(schema.containsTypeWithName("Person"), is(true));
        assertThat(schema.containsScalarWithName("BigDecimal"), is(true));
        generateGraphQLSchema(schema);
    }

    @Test
    public void testMultiLevelPrimitiveArrayAsArgument() throws IOException {
        setupIndex(indexFileName, ArrayAndListQueries.class, MultiLevelListsAndArrays.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);
        Map<String, Object> mapResults = executionContext.execute("query { echo2LevelIntArray(param: [[1, 2], [3, 4]]) }");
        assertThat(mapResults.size(), is(2));
        assertThat(mapResults.get("errors"), is(notNullValue()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testMultiLevelListsAndArraysQueries() throws IOException {
        setupIndex(indexFileName, ArrayAndListQueries.class, MultiLevelListsAndArrays.class);
        ExecutionContext executionContext =  new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { getMultiLevelList { intMultiLevelArray } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("getMultiLevelList");
        ArrayList<ArrayList<Integer>> intArrayList = (ArrayList<ArrayList<Integer>>) mapResults2.get("intMultiLevelArray");
        assertThat(intArrayList, is(notNullValue()));
        ArrayList<Integer> integerArrayList1 = intArrayList.get(0);
        assertThat(integerArrayList1, is(notNullValue()));
        assertThat(integerArrayList1.contains(1), is(true));
        assertThat(integerArrayList1.contains(2), is(true));
        assertThat(integerArrayList1.contains(3), is(true));

        ArrayList<Integer> integerArrayList2 = intArrayList.get(1);
        assertThat(integerArrayList2, is(notNullValue()));
        assertThat(integerArrayList2.contains(4), is(true));
        assertThat(integerArrayList2.contains(5), is(true));
        assertThat(integerArrayList2.contains(6), is(true));

        mapResults = getAndAssertResult(executionContext.execute("query { returnListOfStringArrays }"));
        assertThat(mapResults.size(), is(1));
        ArrayList<ArrayList<String>> stringArrayList = (ArrayList<ArrayList<String>>) mapResults.get("returnListOfStringArrays");
        assertThat(stringArrayList, is(notNullValue()));

        List<String> stringList1 = stringArrayList.get(0);
        assertThat(stringList1, is(notNullValue()));
        assertThat(stringList1.contains("one"), is(true));
        assertThat(stringList1.contains("two"), is(true));
        List<String> stringList2 = stringArrayList.get(1);
        assertThat(stringList2, is(notNullValue()));
        assertThat(stringList2.contains("three"), is(true));
        assertThat(stringList2.contains("four"), is(true));
        assertThat(stringList2.contains("five"), is(true));

        mapResults = getAndAssertResult(executionContext.execute("query { echoLinkedListBigDecimals(param: [-25.926804, 28.203392]) }"));
        assertThat(mapResults.size(), is(1));
        List<BigDecimal> bigDecimalList = (List<BigDecimal>) mapResults.get("echoLinkedListBigDecimals");
        assertThat(bigDecimalList, is(notNullValue()));
        assertThat(bigDecimalList.get(0), is(BigDecimal.valueOf(-25.926804)));
        assertThat(bigDecimalList.get(1), is(new BigDecimal("28.203392")));

        mapResults = getAndAssertResult(executionContext.execute("query { echoListBigDecimal(param: [-25.926804, 28.203392]) }"));
        assertThat(mapResults.size(), is(1));
        bigDecimalList = (List<BigDecimal>) mapResults.get("echoListBigDecimal");
        assertThat(bigDecimalList, is(notNullValue()));
        assertThat(bigDecimalList.get(0), is(BigDecimal.valueOf(-25.926804)));
        assertThat(bigDecimalList.get(1), is(BigDecimal.valueOf(28.203392)));

        mapResults = getAndAssertResult(executionContext.execute("query { echoBigDecimalArray(param: [-25.926804, 28.203392]) }"));
        assertThat(mapResults.size(), is(1));
        bigDecimalList = (List<BigDecimal>) mapResults.get("echoBigDecimalArray");
        assertThat(bigDecimalList, is(notNullValue()));
        assertThat(bigDecimalList.get(0), is(BigDecimal.valueOf(-25.926804)));
        assertThat(bigDecimalList.get(1), is(BigDecimal.valueOf(28.203392)));

        mapResults = getAndAssertResult(executionContext.execute("query { echoIntArray(param: [1, 2]) }"));
        assertThat(mapResults.size(), is(1));
        List<Integer> integerList = (List<Integer>) mapResults.get("echoIntArray");
        assertThat(integerList, is(notNullValue()));
        assertThat(integerList.get(0), is(1));
        assertThat(integerList.get(1), is(2));

        mapResults = getAndAssertResult(executionContext.execute("query { echoShortArray(param: [1, 2]) }"));
        assertThat(mapResults.size(), is(1));
        integerList = (List<Integer>) mapResults.get("echoShortArray");
        assertThat(integerList, is(notNullValue()));
        assertThat(integerList.get(0), is(1));
        assertThat(integerList.get(1), is(2));

        mapResults = getAndAssertResult(executionContext.execute("query { echoLongArray(param: [1, 2]) }"));
        assertThat(mapResults.size(), is(1));
        List<BigInteger> listBigInteger = (List<BigInteger>) mapResults.get("echoLongArray");
        assertThat(listBigInteger, is(notNullValue()));
        assertThat(listBigInteger.get(0), is(BigInteger.valueOf(1)));
        assertThat(listBigInteger.get(1), is(BigInteger.valueOf(2)));

        mapResults = getAndAssertResult(executionContext.execute("query { echoDoubleArray(param: [1.1, 2.2]) }"));
        assertThat(mapResults.size(), is(1));
        List<Double> listDouble = (List<Double>) mapResults.get("echoDoubleArray");
        assertThat(listDouble, is(notNullValue()));
        assertThat(listDouble.get(0), is(Double.valueOf(1.1)));
        assertThat(listDouble.get(1), is(Double.valueOf(2.2)));

        mapResults = getAndAssertResult(executionContext.execute("query { echoBooleanArray(param: [true, false]) }"));
        assertThat(mapResults.size(), is(1));
        List<Boolean> listBoolean = (List<Boolean>) mapResults.get("echoBooleanArray");
        assertThat(listBoolean, is(notNullValue()));
        assertThat(listBoolean.get(0), is(true));
        assertThat(listBoolean.get(1), is(false));

        mapResults = getAndAssertResult(executionContext.execute("query { echoCharArray(param: [\"A\", \"B\"]) }"));
        assertThat(mapResults.size(), is(1));
        List<String> listString = (List<String>) mapResults.get("echoCharArray");
        assertThat(listString, is(notNullValue()));
        assertThat(listString.get(0), is("A"));
        assertThat(listString.get(1), is("B"));

        mapResults = getAndAssertResult(executionContext.execute("query { echoFloatArray(param: [1.01, 2.02]) }"));
        assertThat(mapResults.size(), is(1));
        listDouble = (List<Double>) mapResults.get("echoFloatArray");
        assertThat(listDouble, is(notNullValue()));
        assertThat(listDouble.get(0), is(1.01d));
        assertThat(listDouble.get(1), is(2.02d));

        mapResults = getAndAssertResult(executionContext.execute("query { echoByteArray(param: [0, 1]) }"));
        assertThat(mapResults.size(), is(1));
        List<Integer> listInteger = (List<Integer>) mapResults.get("echoByteArray");
        assertThat(listInteger, is(notNullValue()));
        assertThat(listInteger.get(0), is(0));
        assertThat(listInteger.get(1), is(1));

        SimpleContact contact1 = new SimpleContact("id1", "name1", 1, EnumTestWithEnumName.XL);
        SimpleContact contact2 = new SimpleContact("id2", "name2", 2, EnumTestWithEnumName.S);

        mapResults = getAndAssertResult(executionContext.execute(
                "query { echoSimpleContactArray(param: [ "
                        + generateInput(contact1) + ", "
                        + generateInput(contact2)
                        + " ]) { id name age tShirtSize } }"));
        assertThat(mapResults.size(), is(1));
        List<Map<String, Object>> listContacts = (List<Map<String, Object>>) mapResults.get("echoSimpleContactArray");
        assertThat(listContacts, is(notNullValue()));
        assertThat(listContacts.size(), is(2));
        mapResults2 = listContacts.get(0);
        assertThat(mapResults2, is(not(nullValue())));
        assertThat(mapResults2.get("id"), is(contact1.getId()));
        assertThat(mapResults2.get("name"), is(contact1.getName()));
        assertThat(mapResults2.get("age"), is(contact1.getAge()));
        assertThat(mapResults2.get("tShirtSize"), is(contact1.getTShirtSize().toString()));

        mapResults2 = listContacts.get(1);
        assertThat(mapResults2, is(not(nullValue())));
        assertThat(mapResults2.get("id"), is(contact2.getId()));
        assertThat(mapResults2.get("name"), is(contact2.getName()));
        assertThat(mapResults2.get("age"), is(contact2.getAge()));
        assertThat(mapResults2.get("tShirtSize"), is(contact2.getTShirtSize().toString()));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { processListListBigDecimal(param :[[\"-25.926804 "
                + "longlat\", \"28.203392 longlat\"],[\"-26.926804 longlat\", "
                + " \"27.203392 longlat\"],[\"-27.926804 longlat\", \"26.203392 longlat\"]] ) }"));
        assertThat(mapResults.size(), is(1));
        String result =  (String) mapResults.get("processListListBigDecimal");
        assertThat(result, is(notNullValue()));
        assertThat(result, is("[[-25.926804, 28.203392], [-26.926804, 27.203392], [-27.926804, 26.203392]]"));
    }

    protected String generateInput(SimpleContact contact) {
        return new StringBuilder("{")
                .append("id: ").append(quote(contact.getId())).append(" ")
                .append("name: ").append(quote(contact.getName())).append(" ")
                .append("age: ").append(contact.getAge()).append(" ")
                .append("tShirtSize: ").append(contact.getTShirtSize())
                .append("}")
                .toString();

    }

    protected String quote(String s) {
        return "\"" + s + "\"";
    }

}
