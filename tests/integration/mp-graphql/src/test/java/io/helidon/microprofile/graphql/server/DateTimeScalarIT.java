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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.queries.DateTimeScalarQueries;
import io.helidon.microprofile.graphql.server.test.types.SimpleDateTimePojo;
import io.helidon.microprofile.tests.junit5.AddBean;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for date/time scalars.
 */
@AddBean(DateTimeScalarQueries.class)
@AddBean(TestDB.class)
public class DateTimeScalarIT extends AbstractGraphQLIT {

    @Test
    @SuppressWarnings("unchecked")
    public void testDateAndTime() throws IOException {
        setupIndex(indexFileName, SimpleDateTimePojo.class, DateTimeScalarQueries.class);
        ExecutionContext executionContext = new ExecutionContext(defaultContext);

        Map<String, Object> mapResults = getAndAssertResult(
                executionContext.execute("query { echoSimpleDateTimePojo (dates:[\"2020-01-13\","
                                                 + "\"2021-02-14\"]) { formattedListOfDates } }"));
        assertThat(mapResults.size(), is(1));
        Map<String, Object> mapResults2 = (Map<String, Object>) mapResults.get("echoSimpleDateTimePojo");
        assertThat(mapResults2, is(notNullValue()));
        assertThat(mapResults2.size(), is(1));

        List<String> listDates = (ArrayList<String>) mapResults2.get("formattedListOfDates");
        assertThat(listDates, is(notNullValue()));
        assertThat(listDates.size(), is(2));
        assertThat(listDates.get(0), is("13/01"));
        assertThat(listDates.get(1), is("14/02"));

        mapResults = getAndAssertResult(executionContext.execute("mutation { echoLocalTime(time: \"15:13:00\") }"));
        assertThat(mapResults.size(), is(1));
        assertThat(mapResults.get("echoLocalTime"), is("15:13"));
    }

}
