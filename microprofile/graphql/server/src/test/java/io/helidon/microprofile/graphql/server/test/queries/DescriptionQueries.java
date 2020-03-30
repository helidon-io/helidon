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

import io.helidon.microprofile.graphql.server.test.types.DescriptionType;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries that work with types with various descriptions.
 */
@GraphQLApi
@ApplicationScoped
public class DescriptionQueries {

    public DescriptionQueries() {
    }

    @Query
    public DescriptionType retrieveType() {
        return new DescriptionType("ID1", 10);
    }

    @Query
    public boolean validateType(@Name("type") DescriptionType type) {
        return true;
    }
}
