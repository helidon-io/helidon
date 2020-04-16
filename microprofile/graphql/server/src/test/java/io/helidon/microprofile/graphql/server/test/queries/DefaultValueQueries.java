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

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.DefaultValuePOJO;
import io.helidon.microprofile.graphql.server.test.types.DescriptionType;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries that work with {@link DefaultValue} annotation.
 */
@GraphQLApi
@ApplicationScoped
public class DefaultValueQueries {

    private static final String DEFAULT_INPUT = "{ "
            + " id: \"ID-1\" "
            + " value: 1000 "
            + "}";
            
    @Inject
    private TestDB testDB;
    
    public DefaultValueQueries() {
    }

    @Mutation
    @Name("generateDefaultValuePOJO")
    public DefaultValuePOJO createNewPJO(@Name("id") @DefaultValue("ID-1") String id,
                                         @Name("value") @DefaultValue("1000") int value) {
        return testDB.generatePOJO(id, value);
    }

    @Query
    @Name("echoDefaultValuePOJO")
    public DefaultValuePOJO echo(@Name("input") @DefaultValue(DEFAULT_INPUT) DefaultValuePOJO input) {
        return input;
    }

    @Query
    @Name("echoDefaultValuePOJO2")
    public DefaultValuePOJO echo2(@Name("input") DefaultValuePOJO input) {
        return input;
    }

}
