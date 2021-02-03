/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import io.helidon.graphql.server.InvocationHandler;
import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.SimpleQueriesAndMutations;
import io.helidon.microprofile.graphql.server.test.types.DateTimePojo;
import io.helidon.microprofile.graphql.server.test.types.SimpleDateTime;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static io.helidon.graphql.server.GraphQlConstants.ERRORS;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATETIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_DATE_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.FORMATTED_TIME_SCALAR;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.TIME_SCALAR;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for date/times.
 */
@AddBean(SimpleQueriesAndMutations.class)
@AddBean(TestDB.class)
class DateTimeIT extends AbstractGraphQlCdiIT {

    @Inject
    DateTimeIT(GraphQlCdiExtension graphQlCdiExtension) {
        super(graphQlCdiExtension);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDifferentSetterGetter() throws IOException {
        setupIndex(indexFileName, SimpleDateTime.class, SimpleQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute(
                        "mutation { echoSimpleDateTime(value: { calendarEntries: [ \"22/09/20\", \"23/09/20\" ] } ) { "
                                + "importantDates } }"));
        assertThat(mapResults, is(notNullValue()));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("echoSimpleDateTime");
        assertThat(mapResults2.size(), is(1));
        ArrayList<String> listDates = (ArrayList<String>) mapResults2.get("importantDates");
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("22/09"));
        assertThat(listDates.get(1), is("23/09"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

        Schema schema = createSchema();
        SchemaType type = schema.getTypeByName("DateTimePojo");

        SchemaFieldDefinition fd = getFieldDefinition(type, "localDate");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("MM/dd/yyyy"));
        assertThat(fd.description(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.returnType(), is(FORMATTED_DATE_SCALAR));

        fd = getFieldDefinition(type, "localTime");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("hh:mm[:ss]"));
        assertThat(fd.description(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.returnType(), is(FORMATTED_TIME_SCALAR));

        fd = getFieldDefinition(type, "localDate2");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("MM/dd/yyyy"));
        assertThat(fd.description(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(false));
        assertThat(fd.returnType(), is(FORMATTED_DATE_SCALAR));

        fd = getFieldDefinition(type, "legacyDate");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.format()[0], is("yyyy-MM-dd"));
        assertThat(fd.description(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.returnType(), is(DATE_SCALAR));

        // test default values for date and time
        assertDefaultFormat(type, "offsetTime", "HH[:mm][:ss]Z", true);
        assertDefaultFormat(type, "localTime", "hh:mm[:ss]", false);
        assertDefaultFormat(type, "localDateTime", "yyyy-MM-dd'T'HH[:mm][:ss]", true);
        assertDefaultFormat(type, "offsetDateTime", "yyyy-MM-dd'T'HH[:mm][:ss]Z", true);
        assertDefaultFormat(type, "zonedDateTime", "yyyy-MM-dd'T'HH[:mm][:ss]Z'['VV']'", true);
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
        assertThat(fd.returnType(), is(DATETIME_SCALAR));

        fd = getFieldDefinition(type, "localDateNoFormat");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.returnType(), is(DATE_SCALAR));

        fd = getFieldDefinition(type, "localTimeNoFormat");
        assertThat(fd, is(notNullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.returnType(), is(TIME_SCALAR));

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
        assertThat(fd.format()[0], is("yyyy-MM-dd"));
        assertThat(fd.description(), is(nullValue()));
        assertThat(fd.isDefaultFormatApplied(), is(true));
        assertThat(fd.returnType(), is(DATE_SCALAR));

        mapResults = getAndAssertResult(
                executionContext
                        .execute("query { echoFormattedLocalDateWithReturnFormat(value: [ \"23-09-2020\", \"22-09-2020\" ]) }"));
        assertThat(mapResults, is(notNullValue()));
        listDates = (ArrayList<String>) mapResults.get("echoFormattedLocalDateWithReturnFormat");
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("23/09"));
        assertThat(listDates.get(1), is("22/09"));

        SchemaType typeMutation = schema.getTypeByName("Mutation");
        fd = getFieldDefinition(typeMutation, "echoFormattedDateWithJsonB");
        assertThat(fd, is(notNullValue()));
        Optional<SchemaArgument> argument = fd.arguments()
                .stream().filter(a -> a.argumentName().equals("dates")).findFirst();
        assertThat(argument.isPresent(), is(true));
        SchemaArgument a = argument.get();
        assertThat(a.format()[0], is("MM/dd/yyyy"));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { echoFormattedDateWithJsonB(dates: [ \"09/22/2020\", \"09/23/2020\" ]) }"));
        assertThat(mapResults, is(notNullValue()));
        listDates = (ArrayList<String>) mapResults.get("echoFormattedDateWithJsonB");
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("22/09/2020"));
        assertThat(listDates.get(1), is("23/09/2020"));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { echoOffsetDateTime(value: \"29 Jan 2020 at 09:45 in zone +0200\") }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoOffsetDateTime"), is("2020-01-29T09:45:00+0200"));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { echoZonedDateTime(value: \"19 February 1900 at 12:00 in Africa/Johannesburg\") }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("echoZonedDateTime"), is("1900-02-19T12:00:00+0130[Africa/Johannesburg]"));

        mapResults = getAndAssertResult(executionContext.execute(
                "query { echoLegacyDate(value: \"1968-02-17\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoLegacyDate"), is("1968-02-17"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDatesAndMutations() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

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
        assertThat(mapResults.get("echoLocalDate"), is("1968-02-17"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalDateAU(dateArgument: \"17/02/1968\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoLocalDateAU"), is("17 Feb. 1968"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalDateGB(dateArgument: \"17/02/1968\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoLocalDateGB"), is("17 Feb 1968"));

        mapResults = getAndAssertResult(executionContext.execute("query { queryLocalDateGB }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("queryLocalDateGB"), is("17 Feb 1968"));

        mapResults = getAndAssertResult(executionContext.execute("query { queryLocalDateAU }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("queryLocalDateAU"), is("17 Feb. 1968"));

        Map<String, Object> results = executionContext.execute("mutation { echoLocalDate(dateArgument: \"Today\") }");
        List<Map<String, Object>> listErrors = (List<Map<String, Object>>) results.get(ERRORS);
        assertThat(listErrors, is(notNullValue()));
        assertThat(listErrors.size(), is(1));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalTime(time: \"15:13:00\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoLocalTime"), is("15:13"));

        mapResults = getAndAssertResult(
                executionContext.execute("mutation { testDefaultFormatLocalDateTime(dateTime: \"2020-01-12T10:00:00\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("testDefaultFormatLocalDateTime"), is("10:00:00 12-01-2020"));

        mapResults = getAndAssertResult(
                executionContext.execute("query { transformedDate }"));
        assertThat(mapResults, is(notNullValue()));
        assertThat(mapResults.get("transformedDate"), is("16 Aug. 2016"));
    }

    @Test
    public void testDateInputsAsPojo() throws IOException {
        setupIndex(indexFileName, DateTimePojo.class, SimpleQueriesAndMutations.class);
        InvocationHandler executionContext = createInvocationHandler();

        validateResult(executionContext, "query { echoDateTimePojo ( "
                               + " value: { localDate: \"02/17/1968\" "
                               + "}) { localDate } }",
                       "localDate", "02/17/1968");

        validateResult(executionContext, "query { echoDateTimePojo ( "
                               + " value: { localDate2: \"02/17/1968\" "
                               + "}) { localDate2 } }",
                       "localDate2", "02/17/1968");

        validateResult(executionContext, "query { echoDateTimePojo ( "
                               + " value: { offsetDateTime: \"1968-02-17T10:12:23+0200\" "
                               + "}) { offsetDateTime } }",
                       "offsetDateTime", "1968-02-17T10:12:23+0200");

        validateResult(executionContext, "query { echoDateTimePojo ( "
                               + " value: { zonedDateTime: \"1968-02-17T10:12:23+0200[Africa/Johannesburg]\" "
                               + "}) { zonedDateTime } }",
                       "zonedDateTime", "1968-02-17T10:12:23+0200[Africa/Johannesburg]");


        List<String> listResults = List.of("1968-02-17", "1968-02-18");
        validateResult(executionContext, "query { echoDateTimePojo ( "
                       + " value: { significantDates: [\"1968-02-17\", \"1968-02-18\" ]"
                       + "}) { significantDates } }",
               "significantDates", List.of("1968-02-17", "1968-02-18"));

        // TODO: Fix
//        validateResult(executionContext, "query { echoDateTimePojo ( "
//                               + " value: { localTime: \"10:22:00\" "
//                               + "}) { localTime } }",
//                       "localTime", "10:22:00");

    }

    @SuppressWarnings("unchecked")
    private void validateResult(InvocationHandler executionContext, String query, String field, Object expectedResult) {
        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute(query));
        assertThat(mapResults, is(notNullValue()));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("echoDateTimePojo");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.get(field), is(expectedResult));
    }

}
