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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.NumberFormatQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesWithArgs;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;

import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.INT;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.STRING;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for Number formats.
 */
@AddBean(SimpleContactWithNumberFormats.class)
@AddBean(NumberFormatQueriesAndMutations.class)
@AddBean(SimpleQueriesWithArgs.class)
@AddBean(TestDB.class)
public class NumberFormatIT extends AbstractGraphQLIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testNumberFormats() throws IOException {
        setupIndex(indexFileName, SimpleContactWithNumberFormats.class,
                   NumberFormatQueriesAndMutations.class, SimpleQueriesWithArgs.class);
        ExecutionContext executionContext = createContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(executionContext
                                                                    .execute("query { simpleFormattingQuery { id name age "
                                                                                     + "bankBalance value longValue bigDecimal "
                                                                                     + "} }"));
        assertThat(mapResults.size(), is(1));

        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("simpleFormattingQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is("1 id"));
        assertThat(mapResults2.get("name"), is("Tim"));
        assertThat(mapResults2.get("age"), is("50 years old"));
        assertThat(mapResults2.get("bankBalance"), is("$ 1200.00"));
        assertThat(mapResults2.get("value"), is("10 value"));
        assertThat(mapResults2.get("longValue"), is(BigInteger.valueOf(Long.MAX_VALUE)));
        assertThat(mapResults2.get("bigDecimal"), is("BigDecimal-100"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { generateDoubleValue }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("generateDoubleValue"), is("Double-123456789"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { transformedNumber(arg0: 123) }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("transformedNumber"), is("number 123"));

        mapResults = getAndAssertResult(executionContext.execute("query { echoBigDecimalUsingFormat(param1: \"BD-123\") }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoBigDecimalUsingFormat"), is(BigDecimal.valueOf(123.0)));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoBankBalance(bankBalance: \"$ 106,963.87\") }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoBankBalance"), is(Double.valueOf("106963.87")));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoFloat(size: 10.0123) }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoFloat"), is(Double.valueOf("10.0123")));

        mapResults = getAndAssertResult(executionContext.execute("mutation { idNumber(name: \"Tim\", id: 123) }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("idNumber"), is("Tim-123"));
        
        mapResults = getAndAssertResult(executionContext.execute("mutation { echoBigDecimalList(coordinates: [ 10.0123, -23.000 ]) }"));
        assertThat(mapResults, is(notNullValue()));
        List<BigDecimal> listBigDecimals = (List<BigDecimal>) mapResults.get("echoBigDecimalList");
        assertThat(listBigDecimals.get(0), is(BigDecimal.valueOf(10.0123)));
        assertThat(listBigDecimals.get(1), is(BigDecimal.valueOf(-23.000)));

        
        // TODO: COH-21891
        mapResults = getAndAssertResult(
                executionContext.execute("query { listAsString(arg1: [ \"value 12.12\", \"value 33.33\"] ) }"));
        assertThat(mapResults, is(notNullValue()));

        // create a new contact
        String contactInput =
                "contact: {"
                        + "id: 1 "
                        + "name: \"Tim\" "
                        + "age: \"20 years old\" "
                        + "bankBalance: \"$ 1000.01\" "
                        + "value: \"9 value\" "
                        + "longValue: \"LongValue-123\""
                        + "bigDecimal: \"BigDecimal-12345\""
                        + "listOfIntegers: [ \"1 number\", \"2 number\"]"
                        + " } ";

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { createSimpleContactWithNumberFormats (" + contactInput +
                                                 ") { id name } }"));
        assertThat(mapResults.size(), is(1));
        mapResults2 = (Map<String, Object>) mapResults.get("createSimpleContactWithNumberFormats");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get("id"), is("1 id"));
        assertThat(mapResults2.get("name"), is("Tim"));

        mapResults = getAndAssertResult(executionContext.execute("query { echoFormattedListOfIntegers(value: [ \"1 years old\", \"3 "
                                                                         + "years old\", \"53 years old\" ]) }"));
        assertThat(mapResults, is(notNullValue()));

        List<Integer> listResults = (List<Integer>) mapResults.get("echoFormattedListOfIntegers");
        assertThat(listResults.size(), is(3));

   }

    @Test
    public void testCorrectNumberScalarTypesAndFormats() throws IOException {
        setupIndex(indexFileName, SimpleContactWithNumberFormats.class, NumberFormatQueriesAndMutations.class);
        ExecutionContext executionContext = createContext(defaultContext);
        Schema schema = executionContext.getSchema();

        // validate the formats on the type
        SchemaType type = schema.getTypeByName("SimpleContactWithNumberFormats");
        assertThat(type, is(notNullValue()));

        SchemaFieldDefinition fd = getFieldDefinition(type, "id");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("0 'id'"));
        assertThat(fd.returnType(), is(STRING));

        fd = getFieldDefinition(type, "age");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("0 'years old'"));
        assertThat(fd.returnType(), is(STRING));

        // validate the formats on the Input Type
        SchemaType inputType = schema.getInputTypeByName("SimpleContactWithNumberFormatsInput");
        assertThat(inputType, is(notNullValue()));
        fd = getFieldDefinition(inputType, "id");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is(nullValue()));
        assertThat(fd.returnType(), is(INT));

        fd = getFieldDefinition(inputType, "longValue");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("LongValue-##########"));
        assertThat(fd.returnType(), is(STRING));

        fd = getFieldDefinition(inputType, "age");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("0 'years old'"));
        assertThat(fd.returnType(), is(STRING));

        fd = getFieldDefinition(inputType, "listDates");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("DD-MM-YYYY"));
        assertThat(fd.returnType(), is(FORMATTED_DATE_SCALAR));
    }

}
