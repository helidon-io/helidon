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

import graphql.ExecutionInput;

import javax.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries and mutations using {@link graphql.ExecutionInput}.
 */
@GraphQLApi
@ApplicationScoped
public class ExecutionInputQueriesAndMutations {

    public ExecutionInputQueriesAndMutations() {
    }

    @Query
    public String testExecutionInputNoArgs(ExecutionInput input) {
        return input.getQuery();
    }

    @Query
    public String testExecutionInputWithArgs(@Name("name") String name, ExecutionInput input) {
        return name + input.getQuery();
    }

    @Query
    public String testExecutionInputWithArgs2(@Name("name1") String name1, ExecutionInput input, @Name("name2") String name2) {
        return name1 + name2 + input.getQuery();
    }
}
