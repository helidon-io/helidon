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
import java.util.List;
import java.util.Objects;

/**
 * The representation of a GraphQL Enum.
 */
class SchemaEnum extends AbstractDescriptiveElement implements ElementGenerator {

    /**
     * The name of the enum.
     */
    private String name;

    /**
     * The values for the enum.
     */
    private List<String> values;

    /**
     * Construct a {@link SchemaEnum}.
     *
     * @param builder the {@link SchemaDirective.Builder} to construct from
     */
    private SchemaEnum(Builder builder) {
        this.name = builder.name;
        this.values = builder.values;
        description(builder.description);
    }

    /**
     * Fluent API builder to create {@link SchemaEnum}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the name of the {@link SchemaEnum}.
     * @return the name of the {@link SchemaEnum}
     */
    public String name() {
        return name;
    }

    /**
     * Set the name of the {@link SchemaEnum}.
     * @param name the name of the {@link SchemaEnum}
     */
    public void name(String name) {
        this.name = name;
    }

    /**
     * Return the values for the {@link SchemaEnum}.
     * @return the values for the {@link SchemaEnum}
     */
    public List<String> values() {
        return values;
    }

    /**
     * Add a value to the {@link SchemaEnum}.
     * @param value value to add
     */
    public void addValue(String value) {
        this.values.add(value);
    }

    @Override
    public String getSchemaAsString() {
        StringBuilder sb = new StringBuilder(getSchemaElementDescription(null))
           .append("enum")
           .append(SPACER)
           .append(name())
           .append(SPACER)
           .append(OPEN_CURLY)
           .append(NEWLINE);

        values.forEach(v -> sb.append(SPACER).append(v).append(NEWLINE));

        return sb.append(CLOSE_CURLY).append(NEWLINE).toString();
    }

    @Override
    public String toString() {
        return "Enum{"
                + "name='" + name + '\''
                + ", values=" + values
                + ", description='" + description() + '\'' + '}';
    }

    /**
     * A fluent API {@link io.helidon.common.Builder} to build instances of {@link SchemaDirective}.
     */
    public static class Builder implements io.helidon.common.Builder<SchemaEnum> {

        private String name;
        private List<String> values = new ArrayList<>();
        private String description;

        /**
         * Set the name.
         *
         * @param name the name of the {@link SchemaEnum}
         * @return updated builder instance
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Add a value to the {@link SchemaEnum}.
         *
         * @param value value to add
         * @return updated builder instance
         */
        public Builder addValue(String value) {
            this.values.add(value);
            return this;
        }

        /**
         * Set the description.
         * @param description the description of the {@link SchemaEnum}
         * @return updated builder instance
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        @Override
        public SchemaEnum build() {
            Objects.requireNonNull(name, "Name must be specified");
            return new SchemaEnum(this);
        }
    }

}
