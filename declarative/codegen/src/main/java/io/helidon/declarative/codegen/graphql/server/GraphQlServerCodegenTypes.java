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

package io.helidon.declarative.codegen.graphql.server;

import io.helidon.common.types.TypeName;

final class GraphQlServerCodegenTypes {
    static final TypeName GRAPHQL_QUERY = TypeName.create("io.helidon.graphql.GraphQl.Query");
    static final TypeName GRAPHQL_MUTATION = TypeName.create("io.helidon.graphql.GraphQl.Mutation");
    static final TypeName GRAPHQL_ENTITY = TypeName.create("io.helidon.graphql.GraphQl.Entity");
    static final TypeName GRAPHQL_ARGUMENT = TypeName.create("io.helidon.graphql.GraphQl.Argument");
    static final TypeName GRAPHQL_NAME = TypeName.create("io.helidon.graphql.GraphQl.Name");
    static final TypeName GRAPHQL_DESCRIPTION = TypeName.create("io.helidon.graphql.GraphQl.Description");
    static final TypeName GRAPHQL_DEFAULT_VALUE = TypeName.create("io.helidon.graphql.GraphQl.DefaultValue");
    static final TypeName GRAPHQL_NON_NULL = TypeName.create("io.helidon.graphql.GraphQl.NonNull");
    static final TypeName GRAPHQL_IGNORE = TypeName.create("io.helidon.graphql.GraphQl.Ignore");
    static final TypeName GRAPHQL_SCALAR = TypeName.create("io.helidon.graphql.GraphQl.Scalar");
    static final TypeName GRAPHQL_SCALAR_SPI = TypeName.create("io.helidon.graphql.spi.GraphQlScalar");

    static final TypeName COMMON_CONTEXT = TypeName.create("io.helidon.common.context.Context");
    static final TypeName GRAPHQL_EXECUTION_CONTEXT = TypeName.create("io.helidon.graphql.server.ExecutionContext");
    static final TypeName SECURITY_AUDITED = TypeName.create("io.helidon.security.annotations.Audited");
    static final TypeName SECURITY_AUTHENTICATED = TypeName.create("io.helidon.security.annotations.Authenticated");
    static final TypeName SECURITY_AUTHORIZED = TypeName.create("io.helidon.security.annotations.Authorized");
    static final TypeName SECURITY_CONTEXT = TypeName.create("io.helidon.security.SecurityContext");

    static final TypeName GRAPHQL_SERVER_ENDPOINT = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.Endpoint");
    static final TypeName GRAPHQL_SERVER_LISTENER = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.Listener");
    static final TypeName GRAPHQL_SERVER_CONTEXT = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.Context");
    static final TypeName GRAPHQL_SERVER_SCHEMA_URI = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.SchemaUri");
    static final TypeName GRAPHQL_SERVER_FIELD = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.Field");
    static final TypeName GRAPHQL_SERVER_SOURCE = TypeName.create("io.helidon.webserver.graphql.GraphQlServer.Source");

    static final TypeName DATA_FETCHING_ENVIRONMENT = TypeName.create("graphql.schema.DataFetchingEnvironment");
    static final TypeName GRAPHQL_CONTEXT = TypeName.create("graphql.GraphQLContext");
    static final TypeName GRAPHQL_SCALAR_TYPE = TypeName.create("graphql.schema.GraphQLScalarType");
    static final TypeName COERCED_VARIABLES = TypeName.create("graphql.execution.CoercedVariables");
    static final TypeName COERCING = TypeName.create("graphql.schema.Coercing");
    static final TypeName COERCING_PARSE_LITERAL_EXCEPTION =
            TypeName.create("graphql.schema.CoercingParseLiteralException");
    static final TypeName COERCING_PARSE_VALUE_EXCEPTION = TypeName.create("graphql.schema.CoercingParseValueException");
    static final TypeName COERCING_SERIALIZE_EXCEPTION = TypeName.create("graphql.schema.CoercingSerializeException");
    static final TypeName GRAPHQL_VALUE = TypeName.create("graphql.language.Value");
    static final TypeName GRAPHQL_SCHEMA = TypeName.create("graphql.schema.GraphQLSchema");
    static final TypeName RUNTIME_WIRING = TypeName.create("graphql.schema.idl.RuntimeWiring");
    static final TypeName SCHEMA_GENERATOR = TypeName.create("graphql.schema.idl.SchemaGenerator");
    static final TypeName SCHEMA_PARSER = TypeName.create("graphql.schema.idl.SchemaParser");
    static final TypeName TYPE_DEFINITION_REGISTRY = TypeName.create("graphql.schema.idl.TypeDefinitionRegistry");

    static final TypeName INVOCATION_HANDLER = TypeName.create("io.helidon.graphql.server.InvocationHandler");
    static final TypeName SERVER_HTTP_FEATURE = TypeName.create("io.helidon.webserver.http.HttpFeature");
    static final TypeName SERVER_HTTP_ROUTING_BUILDER = TypeName.create("io.helidon.webserver.http.HttpRouting.Builder");
    static final TypeName HTTP_ENTRY_POINTS = TypeName.create("io.helidon.webserver.http.HttpEntryPoint.EntryPoints");
    static final TypeName GRAPHQL_SERVICE = TypeName.create("io.helidon.webserver.graphql.GraphQlService");
    static final TypeName GRAPHQL_ENTRY_POINTS = TypeName.create("io.helidon.webserver.graphql.GraphQlEntryPoint.EntryPoints");

    private GraphQlServerCodegenTypes() {
    }
}
