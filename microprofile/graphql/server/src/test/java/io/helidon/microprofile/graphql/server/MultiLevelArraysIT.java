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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.ArrayAndListQueries;
import io.helidon.microprofile.graphql.server.test.types.MultiLevelListsAndArrays;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import org.junit.jupiter.api.Test;

/**
 * Tests for Multi-level arrays.
 */
@HelidonTest
@DisableDiscovery
@AddExtension(GraphQLCdiExtension.class)
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
        assertThat(bigDecimalList.get(1), is(new BigDecimal("28.203392")));
    }

}
