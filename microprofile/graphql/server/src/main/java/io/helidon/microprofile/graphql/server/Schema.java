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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import graphql.GraphQLException;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.helidon.microprofile.graphql.server.SchemaGeneratorHelper.getSafeClass;

/**
 * The representation of a GraphQL Schema.
 */
class Schema implements ElementGenerator {

    private static final Logger LOGGER = Logger.getLogger(Schema.class.getName());

    /**
     * Default query name.
     */
    public static final String QUERY = "Query";

    /**
     * Default mutation name.
     */
    public static final String MUTATION = "Mutation";

    /**
     * Default subscription name.
     */
    public static final String SUBSCRIPTION = "Subscription";

    /**
     * The top level query name.
     */
    private String queryName;

    /**
     * The top level mutation name.
     */
    private String mutationName;

    /**
     * The top level subscription name.
     */
    private String subscriptionName;

    /**
     * List of {@link SchemaType}s for this schema. This includes the standard schema types and other types.
     */
    private final List<SchemaType> listSchemaTypes;

    /**
     * List of {@link SchemaScalar}s that should be included in the schema.
     */
    private final List<SchemaScalar> listSchemaScalars;

    /**
     * List of {@link SchemaDirective}s that should be included in the schema.
     */
    private final List<SchemaDirective> listSchemaDirectives;

    /**
     * List of {@link SchemaInputType}s that should be included in the schema.
     */
    private final List<SchemaInputType> listInputTypes;

    /**
     * List of {@link SchemaEnum}s that should be included in the schema.
     */
    private final List<SchemaEnum> listSchemaEnums;

    /**
     * Construct a {@link Schema}.
     *
     * @param builder the {@link Builder} to construct from
     */
    private Schema(Builder builder) {
        this.listSchemaTypes = new ArrayList<>();
        this.listSchemaScalars = new ArrayList<>();
        this.listSchemaDirectives = new ArrayList<>();
        this.listInputTypes = new ArrayList<>();
        this.listSchemaEnums = new ArrayList<>();
        this.queryName = builder.queryName;
        this.subscriptionName = builder.subscriptionName;
        this.mutationName = builder.mutationName;
    }

    /**
     * Fluent API builder to create {@link Schema}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Build a new {@link Schema}.
     *
     * @return a new {@link Schema}
     */
    public static Schema create() {
        return builder().build();
    }

    /**
     * Generates a {@link GraphQLSchema} from the current discovered schema.
     *
     * @return {@link GraphQLSchema}
     */
    public GraphQLSchema generateGraphQLSchema() {
        SchemaParser schemaParser = new SchemaParser();
        TypeDefinitionRegistry typeDefinitionRegistry;

        try {
            typeDefinitionRegistry = schemaParser.parse(getSchemaAsString());
            return new graphql.schema.idl.SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, getRuntimeWiring());
        } catch (Exception e) {
            String message = "Unable to parse the generated schema";
            LOGGER.warning(message + "\n" + getSchemaAsString());
            throw new GraphQLException(message, e);
        }
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder();

        listSchemaDirectives.forEach(d -> sb.append(d.getSchemaAsString()).append('\n'));
        if (listSchemaDirectives.size() > 0) {
            sb.append('\n');
        }

        sb.append("schema ").append(OPEN_CURLY).append(NEWLINE);

        // only output "query", "mutation" and "subscription" types if the
        // type has at least one field definition
        SchemaType queryType = getTypeByName(queryName);
        SchemaType mutationType = getTypeByName(mutationName);
        SchemaType subscriptionType = getTypeByName(subscriptionName);

        if (queryType != null && queryType.hasFieldDefinitions()) {
            sb.append(SPACER).append("query: ").append(queryName).append('\n');
        }
        if (mutationType != null && mutationType.hasFieldDefinitions()) {
            sb.append(SPACER).append("mutation: ").append(mutationName).append('\n');
        }
        if (subscriptionType != null && subscriptionType.hasFieldDefinitions()) {
            sb.append(SPACER).append("subscription: ").append(subscriptionName).append('\n');
        }

        sb.append(CLOSE_CURLY).append(NEWLINE).append(NEWLINE);

        listSchemaTypes.forEach(t -> sb.append(t.getSchemaAsString()).append("\n"));

        listInputTypes.forEach(s -> sb.append(s.getSchemaAsString()).append('\n'));

        listSchemaEnums.forEach(e -> sb.append(e.getSchemaAsString()).append('\n'));

        listSchemaScalars.forEach(s -> sb.append(s.getSchemaAsString()).append('\n'));

        return sb.toString();
    }

    /**
     * Return the {@link RuntimeWiring} for the given auto-generated schema.
     *
     * @return the {@link RuntimeWiring}
     */
    @SuppressWarnings("checkstyle:RegexpSinglelineJava")
    public RuntimeWiring getRuntimeWiring() {
        RuntimeWiring.Builder builder = newRuntimeWiring();

        //  Create the top level Query Runtime Wiring.
        SchemaType querySchemaType = getTypeByName(getQueryName());

        if (querySchemaType == null) {
            throw new GraphQLException("No type exists for query of name " + getQueryName());
        }

        final TypeRuntimeWiring.Builder typeRuntimeBuilder = newTypeWiring(getQueryName());

        // register a type resolver for any interfaces if we have at least one
        Set<SchemaType> setInterfaces = getTypes().stream().filter(SchemaType::isInterface).collect(Collectors.toSet());
        if (setInterfaces.size() > 0) {
            final Map<String, String> mapTypes = new HashMap<>();

            getTypes().stream().filter(t -> !t.isInterface()).forEach(t -> mapTypes.put(t.name(), t.valueClassName()));

            // generate a TypeResolver for all types that are not interfaces
            TypeResolver typeResolver = env -> {
                Object o = env.getObject();
                for (Map.Entry<String, String> entry : mapTypes.entrySet()) {
                    String valueClass = entry.getValue();
                    if (valueClass != null) {
                        Class<?> typeClass = getSafeClass(entry.getValue());
                        if (typeClass != null && typeClass.isAssignableFrom(o.getClass())) {
                            return (GraphQLObjectType) env.getSchema().getType(entry.getKey());
                        }
                    }
                }
                return null;
            };

            // add the type resolver to all interfaces and the Query object
            setInterfaces.forEach(t -> builder.type(t.name(), tr -> tr.typeResolver(typeResolver)));
            builder.type(getQueryName(), tr -> tr.typeResolver(typeResolver));
        }

        // register the scalars
        getScalars().forEach(s -> {
            LOGGER.finest("Register Scalar: " + s);
            builder.scalar(s.graphQLScalarType());
        });

        // we should now have the query runtime binding
        builder.type(typeRuntimeBuilder);

        // search for any types that have field definitions with DataFetchers
        getTypes().forEach(t -> {
            boolean hasDataFetchers = t.fieldDefinitions().stream().anyMatch(fd -> fd.dataFetcher() != null);
            if (hasDataFetchers) {
                final TypeRuntimeWiring.Builder runtimeBuilder = newTypeWiring(t.name());
                t.fieldDefinitions().stream()
                        .filter(fd -> fd.dataFetcher() != null)
                        .forEach(fd -> runtimeBuilder.dataFetcher(fd.name(), fd.dataFetcher()));
                builder.type(runtimeBuilder);
            }
        });

        return builder.build();
    }

    /**
     * Return a {@link SchemaType} that matches the type name.
     *
     * @param typeName type name to match
     * @return a {@link SchemaType} that matches the type name or null if none found
     */
    public SchemaType getTypeByName(String typeName) {
        for (SchemaType schemaType : listSchemaTypes) {
            if (schemaType.name().equals(typeName)) {
                return schemaType;
            }
        }
        return null;
    }

    /**
     * Return a {@link SchemaInputType} that matches the type name.
     *
     * @param inputTypeName type name to match
     * @return a {@link SchemaInputType} that matches the type name or null if none found
     */
    public SchemaInputType getInputTypeByName(String inputTypeName) {
        for (SchemaInputType schemaInputType : listInputTypes) {
            if (schemaInputType.name().equals(inputTypeName)) {
                return schemaInputType;
            }
        }
        return null;
    }

    /**
     * Return a {@link SchemaType} that matches the given class.
     *
     * @param clazz the class to find
     * @return a {@link SchemaType} that matches the given class or null if none found
     */
    public SchemaType getTypeByClass(String clazz) {
        for (SchemaType schemaType : listSchemaTypes) {
            if (clazz.equals(schemaType.valueClassName())) {
                return schemaType;
            }
        }
        return null;
    }

    /**
     * Return a {@link SchemaEnum} that matches the enum name.
     *
     * @param enumName type name to match
     * @return a {@link SchemaEnum} that matches the enum name or null if none found
     */
    public SchemaEnum getEnumByName(String enumName) {
        for (SchemaEnum schemaEnum1 : listSchemaEnums) {
            if (schemaEnum1.name().equals(enumName)) {
                return schemaEnum1;
            }
        }
        return null;
    }

    /**
     * Return a {@link SchemaScalar} which matches the provided class name.
     *
     * @param actualClazz the class name to match
     * @return {@link SchemaScalar} or null if none found
     */
    public SchemaScalar getScalarByActualClass(String actualClazz) {
        for (SchemaScalar schemaScalar : getScalars()) {
            if (schemaScalar.actualClass().equals(actualClazz)) {
                return schemaScalar;
            }
        }
        return null;
    }

    /**
     * Return a {@link SchemaScalar} which matches the provided scalar name.
     *
     * @param scalarName the scalar name to match
     * @return {@link SchemaScalar} or null if none found
     */
    public SchemaScalar getScalarByName(String scalarName) {
        for (SchemaScalar schemaScalar : getScalars()) {
            if (schemaScalar.name().equals(scalarName)) {
                return schemaScalar;
            }
        }
        return null;
    }

    /**
     * Return true of the {@link SchemaType} with the the given name is present for this {@link Schema}.
     *
     * @param type type name to search for
     * @return true if the type name is contained within the type list
     */
    public boolean containsTypeWithName(String type) {
        return listSchemaTypes.stream().anyMatch(t -> t.name().equals(type));
    }

    /**
     * Return true of the {@link SchemaInputType} with the the given name is present for this {@link Schema}.
     *
     * @param type type name to search for
     * @return true if the type name is contained within the input type list
     */
    public boolean containsInputTypeWithName(String type) {
        return listInputTypes.stream().anyMatch(t -> t.name().equals(type));
    }

    /**
     * Return true of the {@link SchemaScalar} with the the given name is present for this {@link Schema}.
     *
     * @param scalar the scalar name to search for
     * @return true if the scalar name is contained within the scalar list
     */
    public boolean containsScalarWithName(String scalar) {
        return listSchemaScalars.stream().anyMatch(s -> s.name().equals(scalar));
    }

    /**
     * Return true of the {@link SchemaEnum} with the the given name is present for this {@link Schema}.
     *
     * @param enumName the enum name to search for
     * @return true if the enum name is contained within the enum list
     */
    public boolean containsEnumWithName(String enumName) {
        return listSchemaEnums.stream().anyMatch(e -> e.name().equals(enumName));
    }

    /**
     * Add a new {@link SchemaType}.
     *
     * @param schemaType new {@link SchemaType} to add
     */
    public void addType(SchemaType schemaType) {
        listSchemaTypes.add(schemaType);
    }

    /**
     * Add a new {@link SchemaScalar}.
     *
     * @param schemaScalar new {@link SchemaScalar} to add.
     */
    public void addScalar(SchemaScalar schemaScalar) {
        listSchemaScalars.add(schemaScalar);
    }

    /**
     * Add a new {@link SchemaDirective}.
     *
     * @param schemaDirective new {@link SchemaDirective} to add
     */
    public void addDirective(SchemaDirective schemaDirective) {
        listSchemaDirectives.add(schemaDirective);
    }

    /**
     * Add a new {@link SchemaInputType}.
     *
     * @param inputType new {@link SchemaInputType} to add
     */
    public void addInputType(SchemaInputType inputType) {
        listInputTypes.add(inputType);
    }

    /**
     * Add a new {@link SchemaEnum}.
     *
     * @param schemaEnumToAdd new {@link SchemaEnum} to add
     */
    public void addEnum(SchemaEnum schemaEnumToAdd) {
        listSchemaEnums.add(schemaEnumToAdd);
    }

    /**
     * Return the {@link List} of {@link SchemaType}s.
     *
     * @return the {@link List} of {@link SchemaType}s
     */
    public List<SchemaType> getTypes() {
        return listSchemaTypes;
    }

    /**
     * Return the {@link List} of {@link SchemaScalar}s.
     *
     * @return the {@link List} of {@link SchemaScalar}s
     */
    public List<SchemaScalar> getScalars() {
        return listSchemaScalars;
    }

    /**
     * Return the {@link List} of {@link SchemaDirective}s.
     *
     * @return the {@link List} of {@link SchemaDirective}s
     */
    public List<SchemaDirective> getDirectives() {
        return listSchemaDirectives;
    }

    /**
     * Return the {@link List} of {@link SchemaInputType}s.
     *
     * @return the {@link List} of {@link SchemaInputType}s
     */
    public List<SchemaInputType> getInputTypes() {
        return listInputTypes;
    }

    /**
     * Return the {@link List} of {@link SchemaEnum}s.
     *
     * @return the {@link List} of {@link SchemaEnum}s
     */
    public List<SchemaEnum> getEnums() {
        return listSchemaEnums;
    }

    /**
     * Return the query name.
     *
     * @return the query name
     */
    public String getQueryName() {
        return queryName;
    }

    /**
     * Return the mutation name.
     *
     * @return the mutation name.
     */
    public String getMutationName() {
        return mutationName;
    }

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link Schema}.
     */
    public static class Builder implements io.helidon.common.Builder<Schema> {

        private String queryName;
        private String mutationName;
        private String subscriptionName;

        private Builder() {
        }

        /**
         * Set the name for the query type.
         *
         * @param queryName name for the query type
         * @return updated builder instance
         */
        public Builder queryName(String queryName) {
            this.queryName = queryName;
            return this;
        }

        /**
         * Set the name for the mutation type.
         *
         * @param mutationName name for the query type
         * @return updated builder instance
         */
        public Builder mutationName(String mutationName) {
            this.mutationName = mutationName;
            return this;
        }

        /**
         * Set the name for the subscription type.
         *
         * @param subscriptionName name for the query type
         * @return updated builder instance
         */
        public Builder subscriptionName(String subscriptionName) {
            this.subscriptionName = subscriptionName;
            return this;
        }

        @Override
        public Schema build() {
            if (this.queryName == null) {
                this.queryName = QUERY;
            }
            if (this.mutationName == null) {
                this.mutationName = MUTATION;
            }
            if (this.subscriptionName == null) {
                this.subscriptionName = SUBSCRIPTION;
            }
            return new Schema(this);
        }
    }

}
