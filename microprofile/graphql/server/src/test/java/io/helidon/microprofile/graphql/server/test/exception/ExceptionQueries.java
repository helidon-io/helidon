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

package io.helidon.microprofile.graphql.server.test.exception;

import javax.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;
import java.io.IOError;

/**
 * Class that holds queries that raise various exceptions.
 */
@GraphQLApi
@ApplicationScoped
public class ExceptionQueries {

    public ExceptionQueries() {
    }

    @Query
    public String query1() {
        return "hello world";
    }

    @Query
    public String uncheckedQuery1() {
        throw new IOError(new RuntimeException("my exception"));
    }

    @Query
    public String checkedQuery1(@Name("throwException") boolean throwException) throws GraphQLException
    {
        if (throwException) {
            throw new GraphQLException("exception");
        }
        return String.valueOf(throwException);
    }
}
