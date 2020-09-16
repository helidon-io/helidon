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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.TIME_SCALAR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;

import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldJunit5Extension;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(WeldJunit5Extension.class)
public class DateTimeIT extends AbstractGraphQLIT {

    @WeldSetup
    private final WeldInitiator weld = WeldInitiator.of(WeldInitiator.createWeld()
                                                                .addBeanClass(SimpleQueriesAndMutations.class)
                                                                .addBeanClass(DateTimePojo.class)
                                                                .addBeanClass(TestDB.class)
                                                                .addExtension(new GraphQLCdiExtension()));

    @Test
    @SuppressWarnings("unchecked")
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesAndMutations.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);

        Schema schema = executionContext.getSchema();
        SchemaType type = schema.getTypeByName("DateTimePojo");

        SchemaFieldDefinition fd = getFieldDefinition(type, "localDate");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.getReturnType(), is(FORMATTED_DATE_SCALAR));

        fd = getFieldDefinition(type, "localTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("hh:mm:ss"));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.getReturnType(), is(FORMATTED_TIME_SCALAR));

        fd = getFieldDefinition(type, "localDate2");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("MM/dd/yyyy"));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.getReturnType(), is(FORMATTED_DATE_SCALAR));

        // test default values for date and time
        assertDefaultFormat(type, "offsetTime", "HH:mm:ssZ", true);
        assertDefaultFormat(type, "localTime", "hh:mm:ss", false);
        assertDefaultFormat(type, "localDateTime", "yyyy-MM-dd'T'HH:mm:ss", true);
        assertDefaultFormat(type, "offsetDateTime", "yyyy-MM-dd'T'HH:mm:ssZ", true);
        assertDefaultFormat(type, "zonedDateTime", "yyyy-MM-dd'T'HH:mm:ssZ'['VV']'", true);
        assertDefaultFormat(type, "localDateNoFormat", "yyyy-MM-dd", true);
        assertDefaultFormat(type, "significantDates", "yyyy-MM-dd", true);
        assertDefaultFormat(type, "formattedListOfDates", "dd/MM", false);

        // testing the conversion of the following scalars when they have default formatting applied
        // FormattedDate -> Date
        // FormattedTime -> Time
        // FormattedDateTime -> DateTime

        fd = getFieldDefinition(type, "localDateTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.getReturnType(), is(DATETIME_SCALAR));

        fd = getFieldDefinition(type, "localDateNoFormat");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.getReturnType(), is(DATE_SCALAR));

        fd = getFieldDefinition(type, "localTimeNoFormat");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.getReturnType(), is(TIME_SCALAR));

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { dateAndTimePOJOQuery { offsetDateTime offsetTime zonedDateTime "
                                                 + "localDate localDate2 localTime localDateTime significantDates "
                                                 + "formattedListOfDates } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("dateAndTimePOJOQuery");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.size(), is(9));

        assertThat(mapResults2.get("localDate"), is("02/17/1968"));
        assertThat(mapResults2.get("localDate2"), is("08/04/1970"));
        assertThat(mapResults2.get("localTime"), is("10:10:20"));
        assertThat(mapResults2.get("offsetTime"), is("08:10:01+0000"));
        Object significantDates = mapResults2.get("significantDates");
        assertThat(significantDates, is(notNullValue()));
        List<String> listDates = (ArrayList<String>) mapResults2.get("significantDates");
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("1968-02-17"));
        assertThat(listDates.get(1), is("1970-08-04"));

        listDates = (List<String>) mapResults2.get("formattedListOfDates");
        assertThat(listDates, is(notNullValue()));
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("17/02"));
        assertThat(listDates.get(1), is("04/08"));

        mapResults = getAndAssertResult(
                executionContext.execute("query { localDateListFormat }"));
        assertThat(mapResults, is(notNullValue()));
        listDates = (ArrayList<String>) mapResults.get("localDateListFormat");
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("17/02/1968"));
        assertThat(listDates.get(1), is("04/08/1970"));

        // test formats on queries
        SchemaType typeQuery = schema.getTypeByName("Query");
        fd = getFieldDefinition(typeQuery, "localDateNoFormat");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.getFormat()[0], is("yyyy-MM-dd"));
        assertThat(fd.getDescription(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.getReturnType(), is(DATE_SCALAR));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDatesAndMutations() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesAndMutations.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("mutation { dateTimePojoMutation { formattedListOfDates localDateTime } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("dateTimePojoMutation");
        assertThat(mapResults2, is(notNullValue()));

        List<String> listDates = (List<String>) mapResults2.get("formattedListOfDates");
        assertThat(listDates, is(notNullValue()));
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("17/02"));
        assertThat(listDates.get(1), is("04/08"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalDate(dateArgument: \"17/02/1968\") }"));
        assertThat(mapResults.size(), is(1));
        String result = (String) mapResults.get("echoLocalDate");
        assertThat(result, is("1968-02-17"));

        Map<String, Object> results = executionContext.execute("mutation { echoLocalDate(dateArgument: \"Today\") }");
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) results.get(ExecutionContext.ERRORS);
        assertThat(listErrors, is(notNullValue()));
        assertThat(listErrors.size(), is(1));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalTime(time: \"15:13:00\") }"));
        assertThat(mapResults.size(), is(1));
        String localTime = (String) mapResults.get("echoLocalTime");
        assertThat(localTime, is("15:13"));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { testDefaultFormatLocalDateTime(dateTime: \"2020-01-12T10:00:00\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("testDefaultFormatLocalDateTime"), is( "10:00:00 12-01-2020"));

        // TODO: https://github.com/eclipse/microprofile-graphql/issues/306 - 1.0.3 spec most likely
//        mapResults = getAndAssertResult(
//                executionContext.execute("query { transformedDate }"));
//        assertThat(mapResults, is(notNullValue()));
//        result = (String) mapResults.get("transformedDate");
//        assertThat(result, is("16 Aug 2016"));

    }

}
