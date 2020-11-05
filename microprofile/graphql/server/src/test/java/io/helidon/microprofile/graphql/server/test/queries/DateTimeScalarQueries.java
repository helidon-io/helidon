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

package io.helidon.microprofile.graphql.server.test.queries;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.bind.annotation.JsonbDateFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.SimpleDateTimePojo;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds simple query definitions with no-argument.
 */
@GraphQLApi
@ApplicationScoped
public class DateTimeScalarQueries {

    @Inject
    private TestDB testDB;

    public DateTimeScalarQueries() {
    }

    @Query
    public SimpleDateTimePojo echoSimpleDateTimePojo(@Name("dates") List<LocalDate> listDates) {
        return new SimpleDateTimePojo(listDates);
    }

    @Mutation
    @JsonbDateFormat("HH:mm")
    public LocalTime echoLocalTime(@Name("time") LocalTime localTime) {
        return localTime;
    }
}
