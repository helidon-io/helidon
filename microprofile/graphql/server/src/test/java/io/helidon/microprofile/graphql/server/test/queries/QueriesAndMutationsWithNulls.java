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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.NullPOJO;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.time.LocalDate;

import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;


/**
 * Class that holds queries that also have Null.
 */
@GraphQLApi
@ApplicationScoped
public class QueriesAndMutationsWithNulls {

    @Inject
    private TestDB testDB;

    public QueriesAndMutationsWithNulls() {
    }

    @Query("method1NotNull")
    @NonNull
    public String getAString() {
        return "A String";
    }

    @Query("method2NotNull")
    public int getInt() {
        return 1;
    }

    @Query("method3NotNull")
    public Long getLong() {
        return 1L;
    }

    // should be optional
    @Query("paramShouldBeNonMandatory")
    public String query1(@Name("value") String value) {
        return "value";
    }

    // should be optional as even though it's primitive, there is DefaultValue
    @Query("paramShouldBeNonMandatory2")
    public String query2(@Name("value") @DefaultValue("1") int value) {
        return "value";
    }

    // even though we have NonNull, there is a default value so should be optional
    @Query("paramShouldBeNonMandatory3")
    public String query3(@Name("value") @DefaultValue("value") @NonNull String value) {
        return "value";
    }

    // String should be mandatory because of NonNull
    @Query("paramShouldBeMandatory")
    public String query4(@Name("value") @NonNull String value) {
        return value;
    }

    // just to generate NullPOJOInput
    @Query
    public boolean validate(@Name("pojo") NullPOJO nullPOJO) {
        return false;
    }

    // argument should be mandatory
    @Query
    public String testMandatoryArgument(@Name("arg1") int arg1) {
        return null;
    }

    @Mutation("returnNullValues")
    public NullPOJO getNullPOJO() {
        return testDB.getNullPOJO();
    }

    @Mutation("echoNullValue")
    public String echoNullValue(String value) {
        return value;
    }

    @Mutation("echoNullDateValue")
    public LocalDate echoNullDateValue(LocalDate value) {
        return value;
    }
    
}
