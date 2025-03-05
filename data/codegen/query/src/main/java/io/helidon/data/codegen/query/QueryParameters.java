/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.data.codegen.query;

import java.util.Objects;
import java.util.Set;

/**
 * Query parameters with code generation implemented.
 */
public class QueryParameters {

    private final Type type;
    private final Set<Parameter> parameters;

    private QueryParameters(Type type, Set<Parameter> parameters) {
        this.type = type;
        this.parameters = parameters;
    }

    /**
     * Create an instance of {@link QueryParameters}.
     *
     * @param type parameter type
     * @param parameters parameters of the query
     * @return new instance of {@link QueryParameters}
     */
    public static QueryParameters create(Type type, Set<Parameter> parameters) {
        return new QueryParameters(type, Set.copyOf(parameters));
    }

    /**
     * Create new ordinal paramete.
     *
     * @param index index of the parameter, starts from {@code 1} for SQL/JPQL
     * @return new ordinal parameter
     */
    public static Parameter ordinalParameter(int index) {
        return new OrdinalParameter(index);
    }

    /**
     * Create new named parameter.
     *
     * @param name name of the parameter
     * @return new named parameter
     */
    public static Parameter namedParameter(String name) {
        return new NamedParameter(name);
    }

    /**
     * Whether query parameters instance is empty (contains no parameters).
     *
     * @return value of {@code true} when parameters instance is empty or {@code false} otherwise
     */
    public boolean isEmpty() {
        return parameters.isEmpty();
    }

    /**
     * Type of the query parameters.
     *
     * @return the query parameters type
     */
    public Type type() {
        return type;
    }

    /**
     * All query parameters.
     *
     * @return the query parameters
     */
    public Set<Parameter> parameters() {
        return parameters;
    }

    /**
     * Query parameter type.
     */
    public enum Type {
        /** Ordinal parameter, e.g. {@code $1}. */
        ORDINAL,
        /** Named parameter, e.g. {@code :name}. */
        NAMED;
    }

    /**
     * JDQL parameter.
     */
    public interface Parameter {

        /**
         * Code to be generated as JDQL parameter.
         * Returns {@link String} literal for named parameter or {@code int} literal for ordinal parameter.
         *
         * @return JDQL parameter code.
         */
        String code();

        /**
         * Name od the parameter.
         * Returns name of the named parameter or index value of the ordinal parameter.
         *
         * @return name od the parameter
         */
        String name();

        /**
         * Index ot the ordinal parameter.
         *
         * @return ordinal parameter index
         * @throws UnsupportedOperationException for named parameter
         */
        int index();

    }

    private record OrdinalParameter(int index) implements Parameter {

        private OrdinalParameter {
            if (index < 0) {
                throw new IllegalArgumentException("Query parameter index is less than 0");
            }
        }

        @Override
        public String code() {
            return Integer.toString(index);
        }

        @Override
        public String name() {
            return Integer.toString(index);
        }

        @Override
        public int index() {
            return index;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || ((other instanceof OrdinalParameter parameter) && this.index == parameter.index);
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(index);
        }

    }

    private record NamedParameter(String name) implements Parameter {

        private NamedParameter {
            Objects.requireNonNull(name, "Query parameter name is null");
        }

        @Override
        @SuppressWarnings("StringBufferReplaceableByString")
        public String code() {
            // Builder capacity is known
            return (new StringBuilder(name.length() + 2))
                    .append('"')
                    .append(name)
                    .append('"')
                    .toString();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int index() {
            throw new UnsupportedOperationException("Named query parameter does not have index");
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || ((other instanceof NamedParameter parameter) && this.name.equals(parameter.name));
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }

}
