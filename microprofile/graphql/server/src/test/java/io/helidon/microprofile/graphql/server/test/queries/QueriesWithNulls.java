/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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

import io.helidon.microprofile.graphql.server.test.types.NullPOJO;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries that also have Mut.
 */
@GraphQLApi
@ApplicationScoped
public class QueriesWithNulls {

    public QueriesWithNulls() {
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

    // just to generate NuklPOJOInput
    @Query
    public boolean validate(@Name("pojo") NullPOJO nullPOJO) {
        return false;
    }
    
}
