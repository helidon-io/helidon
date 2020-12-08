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

package io.helidon.graphql.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import io.helidon.common.GenericType;
import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.http.Parameters;
import io.helidon.config.Config;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

import graphql.schema.GraphQLSchema;

import static org.eclipse.yasson.YassonConfig.ZERO_TIME_PARSE_DEFAULTING;

/**
 * Support for GraphQL for Helidon WebServer.
 */
public class GraphQlSupport implements Service {
    private static final Logger LOGGER = Logger.getLogger(GraphQlSupport.class.getName());
    private static final Jsonb JSONB = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig()
                                .setProperty(ZERO_TIME_PARSE_DEFAULTING, true)
                                .withNullValues(true).withAdapters())
            .build();
    private static final MessageBodyWriter<Object> JSONB_WRITER = JsonbSupport.writer(JSONB);
    private static final MessageBodyReader<Object> JSONB_READER = JsonbSupport.reader(JSONB);
    @SuppressWarnings("rawtypes")
    private static final GenericType<LinkedHashMap> LINKED_HASH_MAP_GENERIC_TYPE = GenericType.create(LinkedHashMap.class);

    private final String context;
    private final String schemaUri;
    private final InvocationHandler invocationHandler;
    private final CorsEnabledServiceHelper corsEnabled;
    private final ExecutorService executor;

    private GraphQlSupport(Builder builder) {
        this.context = builder.context;
        this.schemaUri = builder.schemaUri;
        this.invocationHandler = builder.handler;
        this.corsEnabled = CorsEnabledServiceHelper.create("GraphQL", builder.crossOriginConfig);
        this.executor = builder.executor.get();
    }

    /**
     * Create GraphQL support for a GraphQL schema.
     *
     * @param schema schema to use for GraphQL
     * @return a new support to register with {@link io.helidon.webserver.WebServer} {@link Routing.Builder}
     */
    public static GraphQlSupport create(GraphQLSchema schema) {
        return builder()
                .invocationHandler(InvocationHandler.create(schema))
                .build();
    }

    /**
     * A builder for fine grained configuration of the support.
     *
     * @return a new fluent API builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void update(Routing.Rules rules) {
        // cors
        rules.any(context, corsEnabled.processor());
        // schema
        rules.get(context + schemaUri, this::graphQlSchema);
        // get and post endpoint for graphQL
        rules.get(context, this::graphQlGet)
                .post(context, this::graphQlPost);
    }

    // handle POST request for GraphQL endpoint
    private void graphQlPost(ServerRequest req, ServerResponse res) {
        JSONB_READER
                .read(req.content(), LINKED_HASH_MAP_GENERIC_TYPE, req.content().readerContext())
                .forSingle(entity -> processRequest(res,
                                                    (String) entity.get("query"),
                                                    (String) entity.get("operationName"),
                                                    toVariableMap(entity.get("variables"))))
                .exceptionallyAccept(res::send);
    }

    // handle GET request for GraphQL endpoint
    private void graphQlGet(ServerRequest req, ServerResponse res) {
        Parameters queryParams = req.queryParams();
        String query = queryParams.first("query").orElseThrow(() -> new IllegalStateException("Query must be defined"));
        String operationName = queryParams.first("operationName").orElse(null);
        Map<String, Object> variables = queryParams.first("variables")
                .map(this::toVariableMap)
                .orElseGet(Map::of);

        processRequest(res, query, operationName, variables);
    }

    // handle GET request to obtain GraphQL schema
    private void graphQlSchema(ServerRequest req, ServerResponse res) {
        res.send(invocationHandler.schemaString());
    }

    private void processRequest(ServerResponse res,
                                String query,
                                String operationName,
                                Map<String, Object> variables) {
        executor.submit(() -> {
            try {
                Map<String, Object> result = invocationHandler.execute(query, operationName, variables);
                res.send(JSONB_WRITER.marshall(result));
            } catch (Error e) {
                res.send(e);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Unexpected exception when executing graphQL request", e);
            }
        });

    }

    private Map<String, Object> toVariableMap(Object variables) {
        if (variables == null) {
            return Map.of();
        }

        if (variables instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            Map<?, ?> variablesMap = (Map<?, ?>) variables;
            variablesMap.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        } else {
            return toVariableMap(String.valueOf(variables));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toVariableMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isBlank()) {
            return Map.of();
        }
        return JSONB.fromJson(jsonString, LinkedHashMap.class);
    }

    /**
     * Fluent API builder to create {@link io.helidon.graphql.server.GraphQlSupport}.
     */
    public static class Builder implements io.helidon.common.Builder<GraphQlSupport> {
        private String context = GraphQlConstants.GRAPHQL_WEB_CONTEXT;
        private String schemaUri = GraphQlConstants.GRAPHQL_SCHEMA_URI;
        private CrossOriginConfig crossOriginConfig;
        private Supplier<? extends ExecutorService> executor;
        private InvocationHandler handler;

        private Builder() {
        }

        @Override
        public GraphQlSupport build() {
            if (handler == null) {
                throw new IllegalStateException("Invocation handler must be defined");
            }

            if (executor == null) {
                executor = ServerThreadPoolSupplier.builder()
                        .name("graphql")
                        .threadNamePrefix("graphql-")
                        .build();
            }

            return new GraphQlSupport(this);
        }

        /**
         * Update builder from configuration.
         *
         * Configuration options:
         * <table class="config">
         * <caption>Optional configuration parameters</caption>
         * <tr>
         *     <th>key</th>
         *     <th>default value</th>
         *     <th>description</th>
         * </tr>
         * <tr>
         *     <td>web-context</td>
         *     <td>{@value io.helidon.graphql.server.GraphQlConstants#GRAPHQL_WEB_CONTEXT}</td>
         *     <td>Context that serves the GraphQL endpoint.</td>
         * </tr>
         * <tr>
         *     <td>schema-uri</td>
         *     <td>{@value io.helidon.graphql.server.GraphQlConstants#GRAPHQL_SCHEMA_URI}</td>
         *     <td>URI that serves the schema (under web context)</td>
         * </tr>
         * <tr>
         *     <td>cors</td>
         *     <td>default CORS configuration</td>
         *     <td>see {@link CrossOriginConfig#create(io.helidon.config.Config)}</td>
         * </tr>
         * <tr>
         *     <td>executor-service</td>
         *     <td>default server thread pool configuration</td>
         *     <td>see {@link io.helidon.common.configurable.ServerThreadPoolSupplier#builder()}</td>
         * </tr>
         * </table>
         *
         * @param config configuration to use
         * @return updated builder instance
         */
        public Builder config(Config config) {
            config.get("web-context").asString().ifPresent(this::webContext);
            config.get("schema-uri").asString().ifPresent(this::schemaUri);
            config.get("cors").as(CrossOriginConfig::create).ifPresent(this::crossOriginConfig);

            if (executor == null) {
                executor = ServerThreadPoolSupplier.builder()
                        .name("graphql")
                        .threadNamePrefix("graphql-")
                        .config(config.get("executor-service"))
                        .build();
            }

            return this;
        }

        /**
         * InvocationHandler to execute GraphQl requests.
         *
         * @param handler handler to use
         * @return updated builder instance
         */
        public Builder invocationHandler(InvocationHandler handler) {
            this.handler = handler;
            return this;
        }

        /**
         * InvocationHandler to execute GraphQl requests.
         *
         * @param handler handler to use
         * @return updated builder instance
         */
        public Builder invocationHandler(Supplier<InvocationHandler> handler) {
            return invocationHandler(handler.get());
        }

        /**
         * Set a new root context for REST API of graphQL.
         *
         * @param path context to use
         * @return updated builder instance
         */
        public Builder webContext(String path) {
            if (path.startsWith("/")) {
                this.context = path;
            } else {
                this.context = "/" + path;
            }
            return this;
        }

        /**
         * Configure URI that will serve the GraphQL schema under the context root.
         *
         * @param uri URI of the schema
         * @return updated builder instance
         */
        public Builder schemaUri(String uri) {
            if (uri.startsWith("/")) {
                this.schemaUri = uri;
            } else {
                this.schemaUri = "/" + uri;
            }

            return this;
        }

        /**
         * Set the CORS config from the specified {@code CrossOriginConfig} object.
         *
         * @param crossOriginConfig {@code CrossOriginConfig} containing CORS set-up
         * @return updated builder instance
         */
        public Builder crossOriginConfig(CrossOriginConfig crossOriginConfig) {
            Objects.requireNonNull(crossOriginConfig, "CrossOriginConfig must be non-null");
            this.crossOriginConfig = crossOriginConfig;
            return this;
        }

        /**
         * Executor service to use for GraphQL processing.
         *
         * @param executor executor service
         * @return updated builder instance
         */
        public Builder executor(ExecutorService executor) {
            this.executor = () -> executor;
            return this;
        }

        /**
         * Executor service to use for GraphQL processing.
         *
         * @param executor executor service
         * @return updated builder instance
         */
        public Builder executor(Supplier<? extends ExecutorService> executor) {
            this.executor = executor;
            return this;
        }
    }
}
