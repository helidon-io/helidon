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

package io.helidon.microprofile.graphql.server.test.queries;

import graphql.schema.DataFetchingEnvironment;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries and mutations using {@link DataFetchingEnvironment}.
 */
@GraphQLApi
@ApplicationScoped
public class DataFetchingEnvironmentQueriesAndMutations {

    public DataFetchingEnvironmentQueriesAndMutations() {
    }

    @Query
    public String testNoArgs(DataFetchingEnvironment env) {
        return env.getField().getName();
    }

    @Query
    public String testWithArgs(@Name("name") String name, DataFetchingEnvironment env) {
        return name + env.getField().getName();
    }

    @Query
    public String testWithArgs2(@Name("name1") String name1, DataFetchingEnvironment env, @Name("name2") String name2) {
        return name1 + name2 + env.getField().getName();
    }
}
