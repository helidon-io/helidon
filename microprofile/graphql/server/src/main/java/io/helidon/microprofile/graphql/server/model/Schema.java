/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.graphql.server.model;

import java.util.ArrayList;
import java.util.List;

import graphql.schema.idl.RuntimeWiring;

import static graphql.schema.idl.RuntimeWiring.newRuntimeWiring;

/**
 * The representation of a GraphQL Schema.
 */
public class Schema implements SchemaGenerator {

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
     * List of {@link Type}s for this schema. This includes the standard schema types and other types.
     */
    private final List<Type> listTypes;

    /**
     * List of {@link Scalar}s that should be included in the schema.
     */
    private final List<Scalar> listScalars;

    /**
     * List of {@link Directive}s that should be included in the schema.
     */
    private final List<Directive> listDirectives;

    /**
     * List of {@link InputType}s that should be included in the schema.
     */
    private final List<InputType> listInputTypes;

    /**
     * List of {@link Enum}s that should be included in the schema.
     */
    private final List<Enum> listEnums;

    /**
     * Construct the DiscoveredSchema using the defaults.
     */
    public Schema() {
        this(QUERY, MUTATION, SUBSCRIPTION);
    }

    /**
     * Construct the DiscoveredSchema using the the provided values.
     *
     * @param queryName         name for the query type
     * @param mutationName      name for the mutation type
     * @param subscriptionName  name for the subscription type
     */
    public Schema(String queryName, String mutationName, String subscriptionName) {
        this.queryName        = queryName;
        this.mutationName     = mutationName;
        this.subscriptionName = subscriptionName;
        this.listTypes        = new ArrayList<>();
        this.listScalars      = new ArrayList<>();
        this.listDirectives   = new ArrayList<>();
        this.listInputTypes   = new ArrayList<>();
        this.listEnums        = new ArrayList<>();
    }

//    /**
//     * Generates a {@link GraphQLSchema} from the current discovered schema.
//     *
//     * @return {@link GraphQLSchema}
//     */
//    public GraphQLSchema generateGraphQLSchema() {
//        SchemaParser schemaParser           = new SchemaParser();
//        TypeDefinitionRegistry typeDefinitionRegistry;
//
//        try {
//            typeDefinitionRegistry = schemaParser.parse(generateGraphQLSchemaAsString());
//            return new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, getRuntimeWiring());
//        }
//        catch (Exception e) {
//            LoggingUtilities.log("Unable to parse the following generated schema:\n" + generateGraphQLSchemaAsString(),
//                                 CacheFactory.LOG_WARN);
//            throw e;
//        }
//    }


    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder();

        listDirectives.forEach(d -> sb.append(d.getSchemaAsString()).append('\n'));
        if (listDirectives.size() > 0) {
            sb.append('\n');
        }

        sb.append("schema ").append(OPEN_CURLY).append(NEWLINE);

        // only output "query" if we have a query type
        if (containsTypeWithName(queryName)) {
            sb.append(SPACER).append("query: ").append(queryName).append('\n');
        }
        if (containsTypeWithName(mutationName)) {
            sb.append(SPACER).append("mutation: ").append(mutationName).append('\n');
        }
        if (containsTypeWithName(subscriptionName)) {
            sb.append(SPACER).append("subscription: ").append(subscriptionName).append('\n');
        }

        sb.append(CLOSE_CURLY).append(NEWLINE).append(NEWLINE);

        listTypes.stream()
                .forEach(t -> sb.append(t.getSchemaAsString()).append("\n"));

        listInputTypes.forEach(s -> sb.append(s.getSchemaAsString()).append('\n'));

        listEnums.forEach(e -> sb.append(e.getSchemaAsString()).append('\n'));

        listScalars.forEach(s -> sb.append(s.getSchemaAsString()).append('\n'));

        return sb.toString();
    }

    /**
     * Returns the {@link RuntimeWiring} for the given auto-generated schema.
     * 
     * @return the {@link RuntimeWiring}
     */
    public RuntimeWiring getRuntimeWiring() {
        RuntimeWiring.Builder builder = newRuntimeWiring();

        //  Create the top level Query Runtime Wiring.
        Type queryType = getTypeByName(getQueryName());
//
//        if (queryType == null) {
//            throw new IllegalStateException("No type exists for query of name " + getQueryName());
//        }
//
//        final TypeRuntimeWiring.Builder typeRuntimeBuilder = newTypeWiring(getQueryName());
//
//        queryType.getFieldDefinitions().forEach(fd -> {
//            String cacheMapping = fd.getCacheMapping();
//            if (fd.isArrayReturnType()) {
//                typeRuntimeBuilder.dataFetcher(fd.getName(),
//                                            DataFetcherUtils.newGenericFilterDataFetcher(cacheMapping));
//            }
//            else {
//                typeRuntimeBuilder.dataFetcher(fd.getName(),
//                                           DataFetcherUtils.newGenericSingleKeyDataFetcher(cacheMapping, "key"));
//            }
//        });
//
//        // register a type resolver for any interfaces if we have at least one
//        Set<Type> setInterfaces = getTypes().stream().filter(Type::isInterface).collect(Collectors.toSet());
//        if (setInterfaces.size() > 0) {
//            final Map<String, String> mapTypes = new HashMap<>();
//
//            getTypes().stream().filter(t -> !t.isInterface()).forEach(t -> mapTypes.put(t.getName(), t.getValueClassName()));
//
//            // generate a TypeResolver for all types that are not interfaces
//            TypeResolver typeResolver = env -> {
//                 Object o = env.getObject();
//                 for (Map.Entry<String, String> entry : mapTypes.entrySet()) {
//                     String valueClass = entry.getValue();
//                     if (valueClass != null) {
//                         Class<?> typeClass = getSafeClass(entry.getValue());
//                         if (typeClass != null && typeClass.isAssignableFrom(o.getClass())) {
//                              return (GraphQLObjectType) env.getSchema().getType(entry.getKey());
//                         }
//                     }
//                 }
//                 return null;
//            };
//
//            // add the type resolver to all interfaces and the Query object
//            setInterfaces.forEach(t -> {
//                builder.type(t.getName(), tr -> tr.typeResolver(typeResolver));
//            });
//            builder.type(getQueryName(), tr -> tr.typeResolver(typeResolver));
//        }
//
//        // register the scalars
//        getScalars().forEach(s -> builder.scalar(s.getGraphQLScalarType()));
//
//        // we should now have the query runtime binding
//        builder.type(typeRuntimeBuilder);
//
//        // search for any types that have field definitions with DataFetchers
//        getTypes().forEach(t -> {
//            boolean hasDataFetchers = t.getFieldDefinitions().stream().filter(fd -> fd.getDataFetcher() != null).count() > 0;
//            if (hasDataFetchers) {
//               final TypeRuntimeWiring.Builder runtimeBuilder = newTypeWiring(t.getName());
//               t.getFieldDefinitions().stream()
//                       .filter(fd -> fd.getDataFetcher() != null)
//                       .forEach(fd -> runtimeBuilder.dataFetcher(fd.getName(), fd.getDataFetcher()));
//               builder.type(runtimeBuilder);
//            }
//        });

        return builder.build();
    }
//
//    /**
//     * Register the Coherence Cache {@link org.dataloader.DataLoader}s with the {@link CoherenceGraphQLContext}.
//     *
//     * @param context {@link CoherenceGraphQLContext}
//     */
//    public void registerDataLoaders(CoherenceGraphQLContext context) {
//        //  Create the top level Query Runtime wiring
//        Type queryType = getTypeByName(getQueryName());
//
//        if (queryType == null) {
//            throw new IllegalStateException("No type exists for query of name " + getQueryName());
//        }
//
//        CoherenceDataLoader coherenceDataLoader = new CoherenceDataLoader(context);
//
//        queryType.getFieldDefinitions().forEach(fd -> {
//            // retrieve the Type for the return type so we can get the mapped cache name
//            Type returnType = getTypeByName(fd.getReturnType());
//            if (!fd.isArrayReturnType()) {
//                // only register DataLoader for non-array return types
//                String cacheMapping = fd.getCacheMapping();
//
//                String returnClassName = returnType == null
//                        ? null
//                        : returnType.getKeyClassName();
//                context.registerDataLoader(cacheMapping, coherenceDataLoader.newDataLoader(cacheMapping, returnClassName));
//            }
//        });
//    }

    /**
     * Return a {@link Type} that matches the type name.
     *
     * @param typeName type name to match
     * @return a {@link Type} that matches the type name or null if none found
     */
    public Type getTypeByName(String typeName) {
        for (Type type : listTypes) {
            if (type.getName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Return a {@link Enum} that matches the enum name.
     *
     * @param enumName type name to match
     * @return a {@link Enum} that matches the enum name or null if none found
     */
    public Enum getEnumByName(String enumName) {
        for (Enum enum1 : listEnums) {
            if (enum1.getName().equals(enumName)) {
                return enum1;
            }
        }
        return null;
    }

    /**
     * Returns a {@link Scalar} which matches the provided class name.
     * @param actualClazz  the class name to match
     * @return {@link Scalar} or null if none found
     */
    public Scalar getScalarByActualClass(String actualClazz) {
        for (Scalar scalar : getScalars()) {
            if (scalar.getActualClass().equals(actualClazz)) {
                return scalar;
            }
        }
        return null;
    }

    /**
     * Returns true of the {@link Type} with the the given name is present for this {@link Schema}.
     *
     * @param type type name to search for
     * @return true if the type name is contained within the type list
     */
    public boolean containsTypeWithName(String type) {
        return listTypes.stream().filter(t -> t.getName().equals(type)).count() == 1;
    }

    /**
     * Returns true of the {@link Scalar} with the the given name is present for this {@link Schema}.
     *
     * @param scalar the scalar name to search for
     * @return true if the scalar name is contained within the scalar list
     */
    public boolean containsScalarWithName(String scalar) {
        return listScalars.stream().filter(s -> s.getName().equals(scalar)).count() == 1;
    }

    /**
     * Returns true of the {@link Enum} with the the given name is present for this {@link Schema}.
     *
     * @param enumName the enum name to search for
     * @return true if the enum name is contained within the enum list
     */
    public boolean containsEnumWithName(String enumName) {
        return listEnums.stream().filter(e -> e.getName().equals(enumName)).count() == 1;
    }

    /**
     * Add a new {@link Type}.
     *
     * @param type new {@link Type} to add
     */
    public void addType(Type type) {
        listTypes.add(type);
    }

    /**
     * Add a new {@link Scalar}.
     *
     * @param scalar new {@link Scalar} to add.
     */
    public void addScalar(Scalar scalar) {
        listScalars.add(scalar);
    }

    /**
     * Add a new {@link Directive}.
     *
     * @param directive new {@link Directive} to add
     */
    public void addDirective(Directive directive) {
        listDirectives.add(directive);
    }

    /**
     * Add a new {@link InputType}.
     *
     * @param inputType new {@link InputType} to add
     */
    public void addInputType(InputType inputType) {
        listInputTypes.add(inputType);
    }

    /**
     * Add a new {@link Enum}.
     *
     * @param enumToAdd new {@link Enum} to add
     */
    public void addEnum(Enum enumToAdd) {
        listEnums.add(enumToAdd);
    }

    /**
     * Return the {@link List} of {@link Type}s.
     * @return the {@link List} of {@link Type}s
     */
    public List<Type> getTypes() {
        return listTypes;
    }

    /**
     * Return the {@link List} of {@link Scalar}s.
     * @return the {@link List} of {@link Scalar}s
     */
    public List<Scalar> getScalars() {
        return listScalars;
    }

    /**
     * Return the {@link List} of {@link Directive}s.
     * @return the {@link List} of {@link Directive}s
     */
    public List<Directive> getDirectives() {
        return listDirectives;
    }

    /**
     * Return the {@link List} of {@link InputType}s.
     * @return the {@link List} of {@link InputType}s
     */
    public List<InputType> getInputTypes() {
        return listInputTypes;
    }

    /**
     * Return the {@link List} of {@link Enum}s.
     * @return the {@link List} of {@link Enum}s
     */
    public List<Enum> getEnums() {
        return listEnums;
    }

    /**
     * Return the query name.
     * @return the query name
     */
    public String getQueryName() {
        return queryName;
    }
}
