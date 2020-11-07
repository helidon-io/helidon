/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The representation of a GraphQL directive.
 */
class SchemaDirective implements ElementGenerator {

    /**
     * The name of the directive.
     */
    private final String name;

    /**
     * The list of arguments for the directive.
     */
    private final List<SchemaArgument> listSchemaArguments;

    /**
     * The locations the directive applies to.
     */
    private final Set<String> setLocations;

    /**
     * Construct a {@link SchemaDirective}.
     *
     * @param builder the {@link Builder} to construct from
     */
    private SchemaDirective(Builder builder) {
        this.name = builder.name;
        this.listSchemaArguments = builder.listSchemaArguments;
        this.setLocations = builder.setLocations;
    }

    /**
     * Fluent API builder to create {@link SchemaDirective}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder("directive @" + name());

        if (listSchemaArguments.size() > 0) {
            sb.append("(");
            AtomicBoolean isFirst = new AtomicBoolean(true);
            listSchemaArguments.forEach(a -> {
                String delim = isFirst.get() ? "" : ", ";
                isFirst.set(false);

                sb.append(delim).append(a.argumentName()).append(": ").append(a.argumentType());
                if (a.mandatory()) {
                    sb.append('!');
                }
            });

            sb.append(")");
        }

        sb.append(" on ")
                .append(setLocations.stream().sequential().collect(Collectors.joining("|")));

        return sb.toString();
    }

    /**
     * Add an {@link SchemaArgument} to the {@link SchemaDirective}.
     *
     * @param schemaArgument the {@link SchemaArgument} to add
     */
    public void addArgument(SchemaArgument schemaArgument) {
        listSchemaArguments.add(schemaArgument);
    }

    /**
     * Add a location to the {@link SchemaDirective}.
     *
     * @param location the location to add
     */
    public void addLocation(String location) {
        setLocations.add(location);
    }

    /**
     * Return the name of the {@link SchemaDirective}.
     *
     * @return the name of the {@link SchemaDirective}
     */
    public String name() {
        return name;
    }

    /**
     * Return the {@link List} of {@link SchemaArgument}s.
     *
     * @return the {@link List} of {@link SchemaArgument}s
     */
    public List<SchemaArgument> arguments() {
        return listSchemaArguments;
    }

    /**
     * Return the {@link Set} of locations.
     *
     * @return the {@link Set} of locations
     */
    public Set<String> locations() {
        return setLocations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaDirective schemaDirective = (SchemaDirective) o;
        return Objects.equals(name, schemaDirective.name)
                && Objects.equals(listSchemaArguments, schemaDirective.listSchemaArguments)
                && Objects.equals(setLocations, schemaDirective.setLocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, listSchemaArguments, setLocations);
    }

    @Override
    public String toString() {
        return "Directive{"
                + "name='" + name + '\''
                + ", listArguments=" + listSchemaArguments
                + ", setLocations=" + setLocations
                + '}';
    }

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link SchemaDirective}.
     */
    public static class Builder implements io.helidon.common.Builder<SchemaDirective> {

        private String name;
        private List<SchemaArgument> listSchemaArguments = new ArrayList<>();
        private Set<String> setLocations = new LinkedHashSet<>();

        /**
         * Set the name.
         *
         * @param name the name
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Add an argument to the {@link SchemaDirective}.
         *
         * @param argument the argument to add to the {@link SchemaDirective}
         * @return updated builder instance
         */
        public Builder addArgument(SchemaArgument argument) {
            listSchemaArguments.add(argument);
            return this;
        }

        /**
         * Add a location to the {@link SchemaDirective}.
         *
         * @param location the location to add to the {@link SchemaDirective}
         * @return updated builder instance
         */
        public Builder addLocation(String location) {
            this.setLocations.add(location);
            return this;
        }

        @Override
        public SchemaDirective build() {
            Objects.requireNonNull(name, "Name must be specified");
            return new SchemaDirective(this);
        }
    }
}
