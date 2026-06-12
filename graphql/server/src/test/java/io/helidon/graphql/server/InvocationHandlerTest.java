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
import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.junit.jupiter.api.Test;

import static io.helidon.graphql.server.GraphQlConstants.DEFAULT_MAX_QUERY_COMPLEXITY;
import static io.helidon.graphql.server.GraphQlConstants.DEFAULT_MAX_QUERY_DEPTH;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvocationHandlerTest {
    @Test
    void shouldUseDocumentedDefaultLimits() {
        assertThat(DEFAULT_MAX_QUERY_DEPTH, is(100));
        assertThat(DEFAULT_MAX_QUERY_COMPLEXITY, is(1000));
    }

    @Test
    void shouldRejectDeepQueryByDefault() {
        int queryDepth = DEFAULT_MAX_QUERY_DEPTH + 10;
        ExecutionState state = new ExecutionState();
        InvocationHandler handler = InvocationHandler.create(recursiveSchema(queryDepth, state));

        Map<String, Object> result = handler.execute(deepQuery(queryDepth));

        assertThat(result.containsKey("errors"), is(true));
        assertThat(firstErrorMessage(result), containsString("maximum query depth exceeded"));
        assertThat(state.childResolverCalls(), is(0));
    }

    @Test
    void shouldAllowQueryWithinDefaultDepth() {
        int queryDepth = 5;
        ExecutionState state = new ExecutionState();
        InvocationHandler handler = InvocationHandler.create(recursiveSchema(queryDepth, state));

        Map<String, Object> result = handler.execute(deepQuery(queryDepth));

        assertThat(result.containsKey("errors"), is(false));
        assertThat(state.childResolverCalls(), is(queryDepth));
        assertThat(leafDepth(result), is(queryDepth));
    }

    @Test
    void shouldRejectConfiguredDeepQueryBeforeResolverExecution() {
        int queryDepth = 20;
        ExecutionState state = new ExecutionState();
        Config config = Config.just(ConfigSources.create(Map.of("max-query-depth", "10",
                                                                "max-query-complexity", "0")));
        InvocationHandler handler = InvocationHandler.builder()
                .config(config)
                .schema(recursiveSchema(queryDepth, state))
                .build();

        Map<String, Object> result = handler.execute(deepQuery(queryDepth));

        assertThat(result.containsKey("errors"), is(true));
        assertThat(firstErrorMessage(result), containsString("maximum query depth exceeded"));
        assertThat(state.childResolverCalls(), is(0));
    }

    @Test
    void shouldRejectNegativeDepthConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of("max-query-depth", "-1")));

        assertThrows(IllegalArgumentException.class, () -> InvocationHandler.builder().config(config));
    }

    @Test
    void shouldRejectConfiguredComplexQueryBeforeResolverExecution() {
        AtomicInteger valueResolverCalls = new AtomicInteger();
        InvocationHandler handler = InvocationHandler.builder()
                .maxQueryDepth(0)
                .maxQueryComplexity(5)
                .schema(aliasSchema(valueResolverCalls))
                .build();

        Map<String, Object> result = handler.execute(aliasQuery(10));

        assertThat(result.containsKey("errors"), is(true));
        assertThat(firstErrorMessage(result), containsString("maximum query complexity exceeded"));
        assertThat(valueResolverCalls.get(), is(0));
    }

    @Test
    void shouldRejectComplexQueryByDefault() {
        AtomicInteger valueResolverCalls = new AtomicInteger();
        InvocationHandler handler = InvocationHandler.create(aliasSchema(valueResolverCalls));

        Map<String, Object> result = handler.execute(aliasQuery(DEFAULT_MAX_QUERY_COMPLEXITY + 1));

        assertThat(result.containsKey("errors"), is(true));
        assertThat(firstErrorMessage(result), containsString("maximum query complexity exceeded"));
        assertThat(valueResolverCalls.get(), is(0));
    }

    @Test
    void shouldRejectNegativeComplexityConfiguration() {
        Config config = Config.just(ConfigSources.create(Map.of("max-query-complexity", "-1")));

        assertThrows(IllegalArgumentException.class, () -> InvocationHandler.builder().config(config));
    }

    @Test
    void shouldAllowLimitsToBeDisabled() {
        int queryDepth = DEFAULT_MAX_QUERY_DEPTH + 10;
        ExecutionState state = new ExecutionState();
        InvocationHandler handler = InvocationHandler.builder()
                .maxQueryDepth(0)
                .maxQueryComplexity(0)
                .schema(recursiveSchema(queryDepth, state))
                .build();

        Map<String, Object> result = handler.execute(deepQuery(queryDepth));

        assertThat(result.containsKey("errors"), is(false));
        assertThat(state.childResolverCalls(), is(queryDepth));
        assertThat(leafDepth(result), is(queryDepth));
    }

    @Test
    void shouldAllowLimitsToBeDisabledFromConfig() {
        Config config = Config.just(ConfigSources.create(Map.of("max-query-depth", "0",
                                                                "max-query-complexity", "0")));

        int queryDepth = DEFAULT_MAX_QUERY_DEPTH + 10;
        ExecutionState state = new ExecutionState();
        InvocationHandler deepQueryHandler = InvocationHandler.builder()
                .config(config)
                .schema(recursiveSchema(queryDepth, state))
                .build();

        Map<String, Object> deepQueryResult = deepQueryHandler.execute(deepQuery(queryDepth));

        assertThat(deepQueryResult.containsKey("errors"), is(false));
        assertThat(state.childResolverCalls(), is(queryDepth));
        assertThat(leafDepth(deepQueryResult), is(queryDepth));

        AtomicInteger valueResolverCalls = new AtomicInteger();
        InvocationHandler complexQueryHandler = InvocationHandler.builder()
                .config(config)
                .schema(aliasSchema(valueResolverCalls))
                .build();

        Map<String, Object> complexQueryResult = complexQueryHandler.execute(aliasQuery(DEFAULT_MAX_QUERY_COMPLEXITY + 1));

        assertThat(complexQueryResult.containsKey("errors"), is(false));
        assertThat(valueResolverCalls.get(), is(DEFAULT_MAX_QUERY_COMPLEXITY + 1));
    }

    private static GraphQLSchema recursiveSchema(int queryDepth, ExecutionState state) {
        String schemaText = ""
                + "type Query {\n"
                + "  root: Node!\n"
                + "}\n"
                + "type Node {\n"
                + "  depth: Int!\n"
                + "  child: Node\n"
                + "}\n";

        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(schemaText);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("root", env -> new NodeValue(0, queryDepth)))
                .type("Node", builder -> builder
                        .dataFetcher("depth", env -> ((NodeValue) env.getSource()).depth)
                        .dataFetcher("child", env -> state.nextNode((NodeValue) env.getSource())))
                .build();

        return new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static GraphQLSchema aliasSchema(AtomicInteger valueResolverCalls) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse("type Query { value: Int! }");
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Query", builder -> builder.dataFetcher("value", env -> {
                    valueResolverCalls.incrementAndGet();
                    return 42;
                }))
                .build();

        return new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);
    }

    private static String deepQuery(int queryDepth) {
        StringBuilder query = new StringBuilder("query DeepProbe { root {");
        for (int i = 0; i < queryDepth; i++) {
            query.append(" child {");
        }
        query.append(" depth");
        for (int i = 0; i < queryDepth; i++) {
            query.append(" }");
        }
        query.append(" } }");
        return query.toString();
    }

    private static String aliasQuery(int aliases) {
        StringBuilder query = new StringBuilder("query AliasProbe {");
        for (int i = 0; i < aliases; i++) {
            query.append(" f").append(i).append(": value");
        }
        query.append(" }");
        return query.toString();
    }

    private static Integer leafDepth(Map<String, Object> result) {
        Object data = result.get("data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return null;
        }

        Object current = dataMap.get("root");
        while (current instanceof Map<?, ?> node) {
            Object child = node.get("child");
            if (child == null) {
                Object depth = node.get("depth");
                return depth instanceof Number number ? number.intValue() : null;
            }
            current = child;
        }

        return null;
    }

    private static String firstErrorMessage(Map<String, Object> result) {
        Object errors = result.get("errors");
        if (errors instanceof Iterable<?> iterable) {
            Object firstError = iterable.iterator().next();
            if (firstError instanceof Map<?, ?> errorMap) {
                return String.valueOf(errorMap.get("message"));
            }
            return String.valueOf(firstError);
        }

        return null;
    }

    private static class ExecutionState {
        private final AtomicInteger childResolverCalls = new AtomicInteger();

        private int childResolverCalls() {
            return childResolverCalls.get();
        }

        private NodeValue nextNode(NodeValue current) {
            if (current.remainingChildren == 0) {
                return null;
            }

            childResolverCalls.incrementAndGet();
            return new NodeValue(current.depth + 1, current.remainingChildren - 1);
        }
    }

    private static final class NodeValue {
        private final int depth;
        private final int remainingChildren;

        private NodeValue(int depth, int remainingChildren) {
            this.depth = depth;
            this.remainingChildren = remainingChildren;
        }
    }
}
