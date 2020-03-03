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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * The representation of a GraphQL directive.
 */
public class Directive implements SchemaGenerator {

    /**
     * The name of the directive.
     */
    private final String name;

    /**
     * The list of arguments for the directive.
     */
    private final List<Argument> listArguments;

    /**
     * The locations the directive applies to.
     */
    private final Set<String> setLocations;

    /**
     * Construct a {@link Directive}.
     *
     * @param name name of the directive
     */
    public Directive(String name) {
        this.name = name;
        this.listArguments = new ArrayList<>();
        this.setLocations = new LinkedHashSet<>();
    }

    /**
     * Return the GraphQL schema representation of the element.
     *
     * @return the GraphQL schema representation of the element.
     */
    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder("directive @" + getName());

        if (listArguments.size() > 0) {
            sb.append("(");
            AtomicBoolean isFirst = new AtomicBoolean(true);
            listArguments.forEach(a -> {
                String delim = isFirst.get() ? "" : ", ";
                isFirst.set(false);

                sb.append(delim).append(a.getArgumentName()).append(": ").append(a.getArgumentType());
                if (a.isMandatory()) {
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
     * Add an {@link Argument} to the {@link Directive}.
     *
     * @param argument the {@link Argument} to add
     */
    public void addArgument(Argument argument) {
        listArguments.add(argument);
    }

    /**
     * Add a location to the {@link Directive}.
     *
     * @param location the location to add
     */
    public void addLocation(String location) {
        setLocations.add(location);
    }

    /**
     * Return the name of the {@link Directive}.
     *
     * @return the name of the {@link Directive}
     */
    public String getName() {
        return name;
    }

    /**
     * Return the {@link List} of {@link Argument}s.
     *
     * @return the {@link List} of {@link Argument}s
     */
    public List<Argument> getArguments() {
        return listArguments;
    }

    /**
     * Return the {@link Set} of locations.
     *
     * @return the {@link Set} of locations
     */
    public Set<String> getLocations() {
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
        Directive directive = (Directive) o;
        return Objects.equals(name, directive.name)
                && Objects.equals(listArguments, directive.listArguments)
                && Objects.equals(setLocations, directive.setLocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, listArguments, setLocations);
    }

    @Override
    public String toString() {
        return "Directive{"
                + "name='" + name + '\''
                + ", listArguments=" + listArguments
                + ", setLocations=" + setLocations
                + '}';
    }

}
