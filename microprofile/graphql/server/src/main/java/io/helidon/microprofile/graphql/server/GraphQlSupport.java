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

package io.helidon.microprofile.graphql.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import io.helidon.common.GenericType;
import io.helidon.common.configurable.ServerThreadPoolSupplier;
import io.helidon.common.configurable.ThreadPoolSupplier;
import io.helidon.common.http.Parameters;
import io.helidon.config.Config;
import io.helidon.media.common.MessageBodyReader;
import io.helidon.media.common.MessageBodyWriter;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;
import io.helidon.webserver.cors.CorsEnabledServiceHelper;
import io.helidon.webserver.cors.CrossOriginConfig;

import graphql.schema.idl.SchemaPrinter;

import static org.eclipse.yasson.YassonConfig.ZERO_TIME_PARSE_DEFAULTING;

/**
 * Support for GraphQL for Helidon WebServer.
 */
public class GraphQlSupport implements Service {
    private static final Jsonb JSONB = JsonbBuilder.newBuilder()
            .withConfig(new JsonbConfig()
                                .setProperty(ZERO_TIME_PARSE_DEFAULTING, true)
                                .withNullValues(true).withAdapters())
            .build();

    private static final MessageBodyWriter<Object> JSONB_WRITER = JsonbSupport.writer(JSONB);
    private static final MessageBodyReader<JsonStructure> JSONP_READER = JsonpSupport.reader();
    private static final GenericType<JsonObject> JSON_OBJECT_GENERIC_TYPE = GenericType.create(JsonObject.class);

    private final String context;
    private final String schemaUri;
    private final ExecutionContext execContext;
    private final String printedSchema;
    private final CorsEnabledServiceHelper corsEnabled;
    private final ExecutorService executor;

    private GraphQlSupport(Builder builder) {
        this.context = builder.context;
        this.schemaUri = builder.schemaUri;
        this.execContext = builder.executionContext;
        this.printedSchema = builder.printedSchema;
        this.corsEnabled = CorsEnabledServiceHelper.create("GraphQL", builder.crossOriginConfig);
        this.executor = builder.executor.get();
    }

    public static GraphQlSupport create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void update(Routing.Rules rules) {
        // cors
        rules.any(context, corsEnabled.processor());
        // schema
        rules.get(context + schemaUri, this::schema);
        // get and post endpoint for graphQL
        rules.get(context, this::graphQlGet)
                .post(context, this::graphQlPost);
    }

    private void graphQlPost(ServerRequest req, ServerResponse res) {
        JSONP_READER
                .read(req.content(), JSON_OBJECT_GENERIC_TYPE, req.content().readerContext())
                .forSingle(entity -> processRequest(res,
                                                    entity.getString("query", null),
                                                    entity.getString("operationName", null),
                                                    toVariableMap(entity)))
                .exceptionallyAccept(res::send);
    }

    private void graphQlGet(ServerRequest req, ServerResponse res) {
        Parameters queryParams = req.queryParams();
        String query = queryParams.first("query").get();
        String operationName = queryParams.first("operationName").get();
        Map<String, Object> variables = queryParams.first("variables")
                .map(this::toVariableMap)
                .orElseGet(Map::of);

        processRequest(res, query, operationName, variables);
    }

    private void processRequest(ServerResponse res,
                                String query,
                                String operationName,
                                Map<String, Object> variables) {
        executor.submit(() -> {
            Map<String, Object> result = execContext.execute(query, operationName, variables);
            res.send(JSONB_WRITER.marshall(result));
        });

    }

    private void schema(ServerRequest req, ServerResponse res) {
        res.send(printedSchema);
    }

    private Map<String, Object> toVariableMap(JsonObject entity) {
        Map<String, Object> result = new LinkedHashMap<>();

        JsonValue variables = entity.get("variables");
        if (variables == null) {
            return result;
        }

        if (variables.getValueType() == JsonValue.ValueType.OBJECT) {
            ((JsonObject)variables).forEach((key, value) -> {
                result.put(key, toVariableValue(value));
            });
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toVariableMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isBlank()) {
            return Map.of();
        }
        return JSONB.fromJson(jsonString, LinkedHashMap.class);
    }

    private Object toVariableValue(JsonValue value) {
        switch (value.getValueType()) {

        case STRING:
            return ((JsonString) value).getString();
        case NUMBER:
            return ((JsonNumber) value).numberValue();
        case TRUE:
            return true;
        case FALSE:
            return false;
        case NULL:
            return null;
        case ARRAY:
        case OBJECT:
        default:
            return value.toString();
        }
    }

    public static class Builder implements io.helidon.common.Builder<GraphQlSupport> {
        private String context = "/graphql";
        private String schemaUri = "/schema.graphql";
        private ExecutionContext executionContext;
        private SchemaPrinter schemaPrinter;
        private String printedSchema;
        private CrossOriginConfig crossOriginConfig;
        private ThreadPoolSupplier executor;

        private Builder() {
        }

        @Override
        public GraphQlSupport build() {
            if (executionContext == null) {
                executionContext = defaultExecutionContext();
            }
            if (schemaPrinter == null) {
                schemaPrinter = executionContext.getSchemaPrinter();
            }
            if (executor == null) {
                executor = ServerThreadPoolSupplier.builder()
                        .name("graphql")
                        .threadNamePrefix("graphql-")
                        .build();
            }

            this.printedSchema = schemaPrinter.print(executionContext.getGraphQLSchema());

            return new GraphQlSupport(this);
        }

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

        private ExecutionContext defaultExecutionContext() {
            return ExecutionContext.builder()
                    .context(DefaultContext.create())
                    .build();
        }

    }
}
