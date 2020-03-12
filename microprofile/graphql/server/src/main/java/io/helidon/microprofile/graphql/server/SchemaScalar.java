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

package io.helidon.microprofile.graphql.server;

import java.util.Objects;

import graphql.schema.GraphQLScalarType;

/**
 * The representation of a GraphQL Scalar.
 */
public class SchemaScalar
        implements ElementGenerator {

    /**
     * Name of the Scalar.
     */
    private String name;

    /**
     * Actual class name.
     */
    private String actualClass;

    /**
     * {@link GraphQLScalarType} to convert this {@link SchemaScalar}.
     */
    private GraphQLScalarType graphQLScalarType;

    /**
     * Construct a {@link SchemaScalar}.
     *
     * @param name              name
     * @param actualClass       actual class name
     * @param graphQLScalarType {@link GraphQLScalarType} to convert this {@link SchemaScalar}.
     */
    public SchemaScalar(String name, String actualClass, GraphQLScalarType graphQLScalarType) {
        this.name = name;
        this.actualClass = actualClass;
        this.graphQLScalarType = graphQLScalarType;
    }

    /**
     * Return the name of the {@link SchemaScalar}.
     *
     * @return the name of the {@link SchemaScalar}
     */
    public String getName() {
        return name;
    }

    /**
     * Return the actual class name of the {@link SchemaScalar}.
     *
     * @return the actual class name of the {@link SchemaScalar}
     */
    public String getActualClass() {
        return actualClass;
    }

    /**
     * Return the {@link GraphQLScalarType} instance.
     *
     * @return the {@link GraphQLScalarType} instance.
     */
    public GraphQLScalarType getGraphQLScalarType() {
        return graphQLScalarType;
    }

    @Override
    public String getSchemaAsString() {
        return new StringBuilder("scalar ")
                .append(getName())
                .toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaScalar schemaScalar = (SchemaScalar) o;
        return Objects.equals(name, schemaScalar.name)
                && Objects.equals(actualClass, schemaScalar.actualClass)
                && Objects.equals(graphQLScalarType, schemaScalar.graphQLScalarType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, actualClass, graphQLScalarType);
    }

    @Override
    public String toString() {
        return "Scalar{"
                + "name='" + name + '\''
                + ", actualClass='" + actualClass + '\''
                + ", graphQLScalarType=" + graphQLScalarType + '}';
    }

}
