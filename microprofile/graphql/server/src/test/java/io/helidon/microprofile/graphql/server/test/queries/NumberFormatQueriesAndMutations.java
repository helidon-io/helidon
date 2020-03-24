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

import java.math.BigDecimal;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.helidon.microprofile.graphql.server.test.db.TestDB;
import io.helidon.microprofile.graphql.server.test.types.SimpleContactWithNumberFormats;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.NumberFormat;
import org.eclipse.microprofile.graphql.Query;

/**
 * Class that holds queries and mutations that have various formatting types.
 */
@GraphQLApi
@ApplicationScoped
public class NumberFormatQueriesAndMutations {

    @Inject
    private TestDB testDB;

    public NumberFormatQueriesAndMutations() {
    }

    @Query("simpleFormattingQuery")
    public SimpleContactWithNumberFormats retrieveFormattedObject() {
        return new SimpleContactWithNumberFormats(1, "Tim", 50, 1200.0f, 10, Long.MAX_VALUE);
    }

    @Mutation("generateDoubleValue")
    @NumberFormat("Double-###########")
    public Double generateDouble() {
        return 123456789d;
    }
}
