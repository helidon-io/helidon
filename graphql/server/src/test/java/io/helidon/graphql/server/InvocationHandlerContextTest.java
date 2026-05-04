/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.graphql.server;

import java.util.Map;
import java.util.Optional;

import graphql.Scalars;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;

import org.junit.jupiter.api.Test;

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvocationHandlerContextTest {
    @Test
    void contextValuesAreAvailableToDataFetchers() {
        InvocationHandler handler = InvocationHandler.builder()
                .schema(schema(InvocationHandlerContextTest::contextValue))
                .addContextHandler(context -> {
                    context.setContextValue("authorization", "Bearer secret");
                    context.setContextValue("requestId", 49L);
                })
                .build();

        Map<String, Object> result = handler.execute("{value}");

        assertThat(((Map<?, ?>) result.get("data")).get("value"), is("Bearer secret:49"));
    }

    @Test
    void contextValuesCanBeReadByType() {
        ExecutionContext context = new ExecutionContextImpl();

        context.setContextValue("answer", 42);

        assertThat(context.contextValue("answer", Integer.class), is(Optional.of(42)));
        assertThat(context.contextValue("missing", Integer.class), is(Optional.empty()));
        assertThrows(ClassCastException.class, () -> context.contextValue("answer", String.class));
    }

    @SuppressWarnings("deprecation")
    private static GraphQLSchema schema(DataFetcher<String> dataFetcher) {
        GraphQLObjectType queryType = newObject()
                .name("Query")
                .field(newFieldDefinition()
                        .name("value")
                        .type(Scalars.GraphQLString)
                        .dataFetcher(dataFetcher))
                .build();

        return GraphQLSchema.newSchema()
                .query(queryType)
                .build();
    }

    @SuppressWarnings("deprecation")
    private static String contextValue(DataFetchingEnvironment env) {
        ExecutionContext context = env.getContext();
        String authorization = context.contextValue("authorization", String.class).orElse("missing");
        Long requestId = context.contextValue("requestId", Long.class).orElse(-1L);

        return authorization + ":" + requestId;
    }
}
